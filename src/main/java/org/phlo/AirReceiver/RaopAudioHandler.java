package org.phlo.AirReceiver;

import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.*;

import javax.crypto.*;
import javax.crypto.spec.*;
import javax.sound.sampled.AudioFormat;

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
	
	private static 	AudioFormat s_audioFormat = new AudioFormat(
		44100 /* sample rate */,
		16 /* bits per sample */,
		2 /* number of channels */,
		true /* signed, unsigned is not supported, on OSX at least */,
		true /* big endian */
	);

	private class RaopRtpAudioToControlRouterUpstreamHandler extends SimpleChannelUpstreamHandler {
		@Override
		public synchronized void messageReceived(ChannelHandlerContext ctx, MessageEvent evt)
			throws Exception
		{
			RaopRtpPacket packet = (RaopRtpPacket)evt.getMessage();
			
			if (!(packet instanceof RaopRtpPacket.Audio)) {
				super.messageReceived(ctx, evt);
				return;
			}
			
			if (evt.getChannel() == m_audioChannel) {
				super.messageReceived(ctx, evt);
				return;
			}

			m_audioChannel.getPipeline().sendUpstream(evt);
		}
	}
	
	private class RaopRtpControlToAudioRouterDownstreamHandler extends SimpleChannelDownstreamHandler {
		@Override
		public void writeRequested(ChannelHandlerContext ctx, MessageEvent evt)
			throws Exception
		{
			RaopRtpPacket packet = (RaopRtpPacket)evt.getMessage();
			
			if (!(packet instanceof RaopRtpPacket.RetransmitRequest)) {
				super.writeRequested(ctx, evt);
				return;
			}
			
			if (evt.getChannel() == m_controlChannel) {
				super.writeRequested(ctx, evt);
				return;
			}
			
			m_controlChannel.write(evt.getMessage(), evt.getRemoteAddress());
		}
	}
		
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
	
	private class RaopRtpRetransmitRequestHandler extends SimpleChannelUpstreamHandler {
		private static final int SequenceGapRetransmitLimit = 32;
		
		private int m_expectedSequence = 0;
		
		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent evt)
			throws Exception
		{
			RaopRtpPacket packet = (RaopRtpPacket)evt.getMessage();
			
			if (!(packet instanceof RaopRtpPacket.AudioTransmit)) {
				super.messageReceived(ctx, evt);
				return;
			}
			
			RaopRtpPacket.AudioTransmit audioPacket = (RaopRtpPacket.AudioTransmit)packet;

			int gap = (0x1000 + audioPacket.getSequence() - m_expectedSequence) % 0x1000;
			if (!audioPacket.getExtension() && (gap > 0) && (gap <= SequenceGapRetransmitLimit)) {
				s_logger.fine("Packets lost or re-ordered, sequence number increased by " + (gap+1) + ", requesting retransmission");
				RaopRtpPacket.RetransmitRequest resendRequestPacket = new RaopRtpPacket.RetransmitRequest();
				resendRequestPacket.setSequenceFirst(m_expectedSequence);
				resendRequestPacket.setSequenceCount(gap);
				evt.getChannel().write(resendRequestPacket, m_clientControlAddress);
			}
			else if (audioPacket.getExtension()) {
				s_logger.fine("Packet stream started with sequence " + audioPacket.getSequence());
			}
			else if (gap > 0) {
				s_logger.warning("Packet sequences seem unsynchronized, received sequence " + packet.getSequence() + " while expecting " + m_expectedSequence);
			}

			m_expectedSequence = (audioPacket.getSequence() + 1) % 0x10000;

			super.messageReceived(ctx, evt);
			return;
		}
	}
	
	private class RaopRtpSyncHandler extends SimpleChannelUpstreamHandler {
		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent evt)
			throws Exception
		{
			if (!(evt.getMessage() instanceof RaopRtpPacket.Sync)) {
				super.messageReceived(ctx, evt);
				return;
			}
			
			if (m_audioOutputQueue == null)
				return;
			
			RaopRtpPacket.Sync syncPacket = (RaopRtpPacket.Sync)evt.getMessage();
			
			if (syncPacket.getExtension())
				m_audioOutputQueue.sync(syncPacket.getNowMinusLatency());
		}
	}
	
	public class RaopRtpAudioEnqueueHandler extends SimpleChannelUpstreamHandler {
		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent evt)
			throws Exception
		{
			if (!(evt.getMessage() instanceof RaopRtpPacket.Audio)) {
				super.messageReceived(ctx, evt);
				return;
			}
			
			if (m_audioOutputQueue == null)
				return;
			
			RaopRtpPacket.Audio audioPacket = (RaopRtpPacket.Audio)evt.getMessage();
			
			byte[] samples = new byte[audioPacket.getPayload().capacity()];
			audioPacket.getPayload().getBytes(0, samples);
			m_audioOutputQueue.enqueue(audioPacket.getTimeStamp(), samples);
			if (s_logger.isLoggable(Level.FINEST))
				s_logger.finest("Packet with sequence " + audioPacket.getSequence() + " for playback at " + audioPacket.getTimeStamp() + " submitted to audio output queue");
		}
	}
	
	private final ChannelHandler m_exceptionLoggingHandler = new ExceptionLoggingHandler();
	private final ChannelHandler m_decodeHandler = new RaopRtpDecodeHandler();
	private final ChannelHandler m_encodeHandler = new RtpEncodeHandler();
	private final ChannelHandler m_packetLoggingHandler = new RtpLoggingHandler();
	private final ChannelHandler m_controlToAudioRouterDownstreamHandler = new RaopRtpControlToAudioRouterDownstreamHandler();
	private final ChannelHandler m_audioToControlRouterUpstreamHandler = new RaopRtpAudioToControlRouterUpstreamHandler();
	private final ChannelHandler m_decryptionHandler = new RaopRtpAudioDecryptionHandler();
	private final ChannelHandler m_resendRequestHandler = new RaopRtpRetransmitRequestHandler();
	private final ChannelHandler m_syncHandler = new RaopRtpSyncHandler();
	private ChannelHandler m_audioDecodeHandler;
	private final ChannelHandler m_audioEnqueueHandler = new RaopRtpAudioEnqueueHandler();
	
	private SocketAddress m_clientControlAddress;
	@SuppressWarnings("unused")
	private SocketAddress m_clientTimingAddress;

	private Channel m_audioChannel;
	private Channel m_controlChannel;
	private Channel m_timingChannel;
	
	private SecretKey m_aesKey;
	private IvParameterSpec m_aesIv;
	
	private AudioOutputQueue m_audioOutputQueue;
	
	@Override
	public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
		throws Exception
	{
		if (m_audioOutputQueue != null) {
			s_logger.info("RTSP connection was shut down, closing audio output queue");
			m_audioOutputQueue.close();
		}
	}
	
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
		else if (RaopRtspMethods.FLUSH.equals(method)) {
			flushReceived(ctx, req);
			return;
		}
		else if (RaopRtspMethods.TEARDOWN.equals(method)) {
			teardownReceived(ctx, req);
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
				m_clientControlAddress = substitutePort((InetSocketAddress)ctx.getChannel().getRemoteAddress(), clientControlPort);
				
				m_controlChannel = createRtpChannel(
					substitutePort((InetSocketAddress)ctx.getChannel().getLocalAddress(), 0),
					RaopRtpChannelType.Control
				);
				s_logger.info("Launched RTP control service on " + m_controlChannel.getLocalAddress());
				responseOptions.add("control_port=" + ((InetSocketAddress)m_controlChannel.getLocalAddress()).getPort());
			}
			else if ("timing_port".equals(key)) {
				int clientTimingPort = Integer.valueOf(value);
				m_clientTimingAddress = substitutePort((InetSocketAddress)ctx.getChannel().getRemoteAddress(), clientTimingPort);

				m_timingChannel = createRtpChannel(
					substitutePort((InetSocketAddress)ctx.getChannel().getLocalAddress(), 0),
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
		throws Exception
	{
		m_audioOutputQueue = new AudioOutputQueue(s_audioFormat);
		
		s_logger.info("Client initiated streaming, audio output queue created");
		
		final HttpResponse response = new DefaultHttpResponse(RtspVersions.RTSP_1_0,  RtspResponseStatuses.OK);
		ctx.getChannel().write(response);
	}
	
	private void flushReceived(ChannelHandlerContext ctx, HttpRequest req) {
		s_logger.info("Client paused streaming");
		
		final HttpResponse response = new DefaultHttpResponse(RtspVersions.RTSP_1_0,  RtspResponseStatuses.OK);
		ctx.getChannel().write(response);
	}

	private void teardownReceived(ChannelHandlerContext ctx, HttpRequest req) {
		s_logger.info("Client stopped streaming, closing RTSP connection");
		
		final HttpResponse response = new DefaultHttpResponse(RtspVersions.RTSP_1_0,  RtspResponseStatuses.OK);
		ctx.getChannel().setReadable(false);
		ctx.getChannel().write(response).addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				future.getChannel().close();
			}
		});
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

	private Channel createRtpChannel(final SocketAddress local, final RaopRtpChannelType channelType)
		throws InterruptedException
	{
		ConnectionlessBootstrap bootstrap = new ConnectionlessBootstrap(new NioDatagramChannelFactory(
			Executors.newCachedThreadPool())
		);
		bootstrap.setOption("receiveBufferSizePredictorFactory", new FixedReceiveBufferSizePredictorFactory(65535));
		bootstrap.setOption("receiveBufferSize", 65535);
		bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
			@Override
			public ChannelPipeline getPipeline() throws Exception {
				ChannelPipeline pipeline = Channels.pipeline();
				
				pipeline.addLast("executionHandler", AirReceiver.ChannelExecutionHandler);
				pipeline.addLast("exceptionLogger", m_exceptionLoggingHandler);
				pipeline.addLast("decoder", m_decodeHandler);
				pipeline.addLast("encoder", m_encodeHandler);
				pipeline.addLast("packetLogger", m_packetLoggingHandler);
				if (channelType.equals(RaopRtpChannelType.Audio))
					pipeline.addLast("audioToControlRouter", m_audioToControlRouterUpstreamHandler);
				if (channelType.equals(RaopRtpChannelType.Control))
					pipeline.addLast("controlToAudioRouter", m_controlToAudioRouterDownstreamHandler);
				if (channelType.equals(RaopRtpChannelType.Audio))
					pipeline.addLast("resendRequester", m_resendRequestHandler);
				if (channelType.equals(RaopRtpChannelType.Audio) && (m_decryptionHandler != null))
					pipeline.addLast("decrypt", m_decryptionHandler);
				if (channelType.equals(RaopRtpChannelType.Audio) && (m_audioDecodeHandler != null))
					pipeline.addLast("audioDecode", m_audioDecodeHandler);
				if (channelType.equals(RaopRtpChannelType.Control))
					pipeline.addLast("sync", m_syncHandler);
				if (channelType.equals(RaopRtpChannelType.Audio))
					pipeline.addLast("enqueue", m_audioEnqueueHandler);
				
				return pipeline;
			}
		});
		
		Channel channel = bootstrap.bind(local);
		return channel;
	}
	
	private InetSocketAddress substitutePort(final InetSocketAddress address, final int port) {
		return new InetSocketAddress(address.getAddress(), port);
	}
}
