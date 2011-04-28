package org.phlo.AirReceiver;

import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.regex.*;

import javax.crypto.*;
import javax.crypto.spec.*;

import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.codec.rtsp.*;

public class RaopAudioHandler extends SimpleChannelUpstreamHandler {
	private static Logger s_logger = Logger.getLogger(RaopAudioHandler.class.getName());

	static enum RaopRtpChannelType { Audio, Control, Timing };
	
	private static final String HeaderTransport = "Transport";
	private static final String HeaderSession = "Session";

	private static final Cipher s_rsaPkCS1OaepCipher = AirTunesKeys.getCipher("RSA/None/OAEPWithSHA1AndMGF1Padding", "BC");
	private static final Cipher s_aesCipher = AirTunesKeys.getCipher("AES/CBC/NoPadding", "BC");

	private class RaopRtpAudioDecryptionHandler extends SimpleChannelUpstreamHandler {
		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent evt)
			throws Exception
		{
			RaopRtpPacket.Audio audioPacket = (RaopRtpPacket.Audio)evt.getMessage();
			ChannelBuffer audioPayload = audioPacket.getPayload();
			
			synchronized(s_aesCipher) {
				s_aesCipher.init(Cipher.DECRYPT_MODE, m_aesKey, m_aesIv);
				for(int i=0; (i + 16) <= audioPayload.capacity(); i += 16) {
					byte[] block = new byte[16];
					audioPayload.getBytes(i, block);
					block = s_aesCipher.update(block);
					audioPayload.setBytes(i, block);
				}
			}
			
			super.messageReceived(ctx, evt);
		}
	}
	
	private class RaopRtpResendRequestHandler extends SimpleChannelUpstreamHandler {
		/* Disabled for now since retransmits requests don't work */
		private static final int SequenceGapLimit = 0;
		
		private int m_expectedSequence = -0x80000000;
		
		@Override
		public synchronized void messageReceived(ChannelHandlerContext ctx, MessageEvent evt)
			throws Exception
		{
			RaopRtpPacket.Audio audioPacket = (RaopRtpPacket.Audio)evt.getMessage();
			
			int gap = audioPacket.getSequence() - m_expectedSequence;
			if ((gap > 0)  && (gap <= SequenceGapLimit)) {
				s_logger.fine("Packet sequence number has fixable gap of " + gap + ", requested resend of " + (gap+1) + " packets starting with " + m_expectedSequence);
				RaopRtpPacket.RetransmitRequest resendRequestPacket = new RaopRtpPacket.RetransmitRequest();
				resendRequestPacket.setSequenceFirst(m_expectedSequence);
				resendRequestPacket.setSequenceCount(gap + 1);
				ctx.getChannel().write(resendRequestPacket, m_controlChannel.getRemoteAddress());
			}
			else {
				if (gap != 0)
					s_logger.fine("Packet sequence number has unfixable gap of " + gap);
				m_expectedSequence = (audioPacket.getSequence() + 1) % 0x10000;
				super.messageReceived(ctx, evt);
			}
		}
	}
	
	private Channel m_audioChannel;
	private Channel m_controlChannel;
	private Channel m_timingChannel;

	private final ChannelHandler m_exceptionLoggingHandler = new ExceptionLoggingHandler();
	private final ChannelHandler m_decodeHandler = new RaopRtpDecodeHandler();
	private final ChannelHandler m_encodeHandler = new RtpEncodeHandler();
	private final ChannelHandler m_packetLoggingHandler = new RtpLoggingHandler();
	private final ChannelHandler m_resendRequestHandler = new RaopRtpResendRequestHandler();
	private final ChannelHandler m_decryptionHandler = new RaopRtpAudioDecryptionHandler();
	private ChannelHandler m_audioDecodeHandler;
	private ChannelHandler m_playbackHandler;
	
	private SecretKey m_aesKey;
	private IvParameterSpec m_aesIv;
	
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent evt) throws Exception {
		HttpRequest req = (HttpRequest)evt.getMessage();
		HttpMethod method = req.getMethod();
		
		if (RaopRtspMethods.ANNOUNCE.equals(method)) {
			announceReceived(ctx, req);
			return;
		}
		else if (RaopRtspMethods.SETUP.equals(method)) {
			setupReceived(ctx, req);
			return;
		}
		else if (RaopRtspMethods.RECORD.equals(method)) {
			recordReceived(ctx, req);
			return;
		}
		else if (RaopRtspMethods.SET_PARAMETER.equals(method)) {
			setParameterReceived(ctx, req);
			return;
		}
		else if (RaopRtspMethods.GET_PARAMETER.equals(method)) {
			getParameterReceived(ctx, req);
			return;
		}
		
		super.messageReceived(ctx, evt);
	}
	
	private static Pattern s_pattern_sdp_line = Pattern.compile("^([a-z])=(.*)$");
	private static Pattern s_pattern_sdp_m = Pattern.compile("^audio ([^ ]+) RTP/AVP ([0-9]+)$");
	private static Pattern s_pattern_sdp_a = Pattern.compile("^([a-z]+):(.*)$");
	private static Pattern s_pattern_sdp_a_rtpmap = Pattern.compile("^([0-9]+) (.*)$");
	public void announceReceived(ChannelHandlerContext ctx, HttpRequest req)
		throws Exception
	{
		if (!req.containsHeader("Content-Type"))
			throw new ProtocolException("No Content-Type header");
		if (!"application/sdp".equals(req.getHeader("Content-Type")))
			throw new ProtocolException("Invalid Content-Type header, expected application/sdp but got " + req.getHeader("Content-Type"));
		
		final String dsp = req.getContent().toString(Charset.forName("ASCII")).replace("\r", "");
		
		m_aesKey = null;
		m_aesIv = null;

		int alacFormatIndex = -1;
		int audioFormatIndex = -1;
		int descriptionFormatIndex = -1;
		String[] formatOptions = null;
		
		for(final String line: dsp.split("\n")) {
			final Matcher line_matcher = s_pattern_sdp_line.matcher(line);
			if (!line_matcher.matches())
				throw new ProtocolException("Cannot parse SDP line " + line);
			char attribute = line_matcher.group(1).charAt(0);
			String setting = line_matcher.group(2);
			
			switch (attribute) {
				case 'm':
					final Matcher m_matcher = s_pattern_sdp_m.matcher(setting);
					if (!m_matcher.matches())
						throw new ProtocolException("Cannot parse SDP " + attribute + "'s setting " + setting);
					audioFormatIndex = Integer.valueOf(m_matcher.group(2));
					break;
					
				case 'a':
					final Matcher a_matcher = s_pattern_sdp_a.matcher(setting);
					if (!a_matcher.matches())
						throw new ProtocolException("Cannot parse SDP " + attribute + "'s setting " + setting);

					final String key = a_matcher.group(1);
					final String value = a_matcher.group(2);
					
					if ("rtpmap".equals(key)) {
						final Matcher a_rtpmap_matcher = s_pattern_sdp_a_rtpmap.matcher(value);
						if (!a_rtpmap_matcher.matches())
							throw new ProtocolException("Cannot parse SDP " + attribute + "'s rtpmap entry " + value);
						
						final int formatIdx = Integer.valueOf(a_rtpmap_matcher.group(1));
						final String format = a_rtpmap_matcher.group(2);
						if ("AppleLossless".equals(format))
							alacFormatIndex = formatIdx;
					}
					else if ("fmtp".equals(key)) {
						final String[] parts = value.split(" ");
						descriptionFormatIndex = Integer.valueOf(parts[0]);
						formatOptions = Arrays.copyOfRange(parts, 1, parts.length);
					}
					else if ("rsaaeskey".equals(key)) {
						byte[] aesKeyRaw;

						synchronized(s_rsaPkCS1OaepCipher) {
							s_rsaPkCS1OaepCipher.init(Cipher.DECRYPT_MODE, AirTunesKeys.PrivateKey);
							aesKeyRaw = s_rsaPkCS1OaepCipher.doFinal(Base64.decodeUnpadded(value));
						}
						
						m_aesKey = new SecretKeySpec(aesKeyRaw, "AES");
					}
					else if ("aesiv".equals(key)) {
						m_aesIv = new IvParameterSpec(Base64.decodeUnpadded(value));
					}
					break;
					
				default:
					/* Ignore */
					break;
			}
		}
		
		if (alacFormatIndex != audioFormatIndex)
			throw new ProtocolException("Audio format " + audioFormatIndex + " not supported");
		if (audioFormatIndex != descriptionFormatIndex)
			throw new ProtocolException("Auido format " + audioFormatIndex + " lacks fmtp line");
		if (formatOptions == null)
			throw new ProtocolException("Auido format " + audioFormatIndex + " incomplete, format options not set");

		m_audioDecodeHandler = new RaopRtpAudioAlacDecodeHandler(formatOptions);
		m_playbackHandler = new RaopAudioPlaybackHandler();
				
		final HttpResponse response = new DefaultHttpResponse(RtspVersions.RTSP_1_0,  RtspResponseStatuses.OK);
		ctx.getChannel().write(response);
	}
	
	private static Pattern s_pattern_transportOption = Pattern.compile("^([A-Za-z0-9_-]+)(=(.*))?$");
	public void setupReceived(ChannelHandlerContext ctx, HttpRequest req)
		throws ProtocolException, InterruptedException
	{
		if (!req.containsHeader(HeaderTransport))
			throw new ProtocolException("No Transport header");
		
		final Deque<String> requestOptions = new java.util.LinkedList<String>(Arrays.asList(req.getHeader(HeaderTransport).split(";")));
		final List<String> responseOptions = new java.util.LinkedList<String>();
		
		final String requestProtocol = requestOptions.removeFirst();
		if (!"RTP/AVP/UDP".equals(requestProtocol))
			throw new ProtocolException("Transport protocol must be RTP/AVP/UDP, but was " + requestProtocol);
		responseOptions.add(requestProtocol);
		
		for(final String requestOption: requestOptions) {
			final Matcher m_transportOption = s_pattern_transportOption.matcher(requestOption);
			if (!m_transportOption.matches())
				throw new ProtocolException("Cannot parse Transport option " + requestOption);
			
			final String key = m_transportOption.group(1);
			final String value = m_transportOption.group(3);
			
			if ("interleaved".equals(key)) {
				if (!"0-1".equals(value))
					throw new ProtocolException("Unsupported Transport option, interleaved must be 0-1 but was " + value);
				responseOptions.add("interleaved=0-1");
			}
			else if ("mode".equals(key)) {
				if (!"record".equals(value))
					throw new ProtocolException("Unsupported Transport option, mode must be record but was " + value);
				responseOptions.add("mode=record");
			}
			else if ("control_port".equals(key)) {
				int clientControlPort = Integer.valueOf(value);
				m_controlChannel = createRtpChannel(
					substitutePort((InetSocketAddress)ctx.getChannel().getLocalAddress(), 0),
					substitutePort((InetSocketAddress)ctx.getChannel().getRemoteAddress(), clientControlPort),
					RaopRtpChannelType.Control
				);
				s_logger.info("Launched RTP control service on " + m_controlChannel.getLocalAddress());
				responseOptions.add("control_port=" + ((InetSocketAddress)m_controlChannel.getLocalAddress()).getPort());
			}
			else if ("timing_port".equals(key)) {
				int clientTimingPort = Integer.valueOf(value);
				m_timingChannel = createRtpChannel(
					substitutePort((InetSocketAddress)ctx.getChannel().getLocalAddress(), 0),
					substitutePort((InetSocketAddress)ctx.getChannel().getRemoteAddress(), clientTimingPort),
					RaopRtpChannelType.Timing
				);
				s_logger.info("Launched RTP timing service on " + m_timingChannel.getLocalAddress());
				responseOptions.add("timing_port=" + ((InetSocketAddress)m_timingChannel.getLocalAddress()).getPort());
			}
			else {
				responseOptions.add(requestOption);
			}
		}
		
		m_audioChannel = createRtpChannel(
			substitutePort((InetSocketAddress)ctx.getChannel().getLocalAddress(), 0),
			null,
			RaopRtpChannelType.Audio
		);
		s_logger.info("Launched RTP audio service on " + m_audioChannel.getLocalAddress());
		responseOptions.add("server_port=" + ((InetSocketAddress)m_audioChannel.getLocalAddress()).getPort());

		final StringBuilder transportResponseBuilder = new StringBuilder();
		for(String responseOption: responseOptions) {
			if (transportResponseBuilder.length() > 0)
				transportResponseBuilder.append(";");
			transportResponseBuilder.append(responseOption);
		}
		
		final HttpResponse response = new DefaultHttpResponse(RtspVersions.RTSP_1_0,  RtspResponseStatuses.OK);
		response.addHeader(HeaderTransport, transportResponseBuilder.toString());
		response.addHeader(HeaderSession, "DEADBEEEF");
		ctx.getChannel().write(response);
		
	}

	public void recordReceived(ChannelHandlerContext ctx, HttpRequest req)
		throws ProtocolException
	{
		s_logger.info("Client initiated streaming");
		
		final HttpResponse response = new DefaultHttpResponse(RtspVersions.RTSP_1_0,  RtspResponseStatuses.OK);
		ctx.getChannel().write(response);
	}
	
	public void setParameterReceived(ChannelHandlerContext ctx, HttpRequest req)
		throws ProtocolException
	{
		// XXX Implement!
		
		final HttpResponse response = new DefaultHttpResponse(RtspVersions.RTSP_1_0,  RtspResponseStatuses.OK);
		ctx.getChannel().write(response);
	}
	
	public void getParameterReceived(ChannelHandlerContext ctx, HttpRequest req)
		throws ProtocolException
	{
		// XXX Implement!

		final HttpResponse response = new DefaultHttpResponse(RtspVersions.RTSP_1_0,  RtspResponseStatuses.OK);
		ctx.getChannel().write(response);
	}

	private Channel createRtpChannel(final SocketAddress local, final SocketAddress remote, final RaopRtpChannelType channelType)
		throws InterruptedException
	{
		ConnectionlessBootstrap bootstrap = new ConnectionlessBootstrap(new NioDatagramChannelFactory(
			Executors.newCachedThreadPool())
		);
		bootstrap.setOption("receiveBufferSizePredictorFactory", new FixedReceiveBufferSizePredictorFactory(1500));
		bootstrap.setOption("receiveBufferSize", 65536);
		bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
			@Override
			public ChannelPipeline getPipeline() throws Exception {
				ChannelPipeline pipeline = Channels.pipeline();
				
				pipeline.addLast("executionHandler", AirReceiver.ChannelExecutionHandler);
				pipeline.addLast("exceptionLogger", m_exceptionLoggingHandler);
				pipeline.addLast("decoder", m_decodeHandler);
				pipeline.addLast("encoder", m_encodeHandler);
				pipeline.addLast("packetLogger", m_packetLoggingHandler);
				if (channelType.equals(RaopRtpChannelType.Audio)) {
					pipeline.addLast("resendRequester", m_resendRequestHandler);					
					if (m_decryptionHandler != null)
						pipeline.addLast("decrypt", m_decryptionHandler);
					if (m_audioDecodeHandler != null)
						pipeline.addLast("decodeAudio", m_audioDecodeHandler);
				}
				pipeline.addLast("playback", m_playbackHandler);
				
				return pipeline;
			}
		});
		
		Channel channel = bootstrap.bind(local);
		if (remote != null)
			channel.connect(remote);
		
		return channel;
	}
	
	private InetSocketAddress substitutePort(final InetSocketAddress address, final int port) {
		return new InetSocketAddress(address.getAddress(), port);
	}
}
