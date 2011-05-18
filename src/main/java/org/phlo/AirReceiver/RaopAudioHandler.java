/*
 * This file is part of AirReceiver.
 * 
 * AirReceiver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * AirReceiver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with AirReceiver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.phlo.AirReceiver;

import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.*;

import javax.crypto.*;
import javax.crypto.spec.*;

import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.group.*;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.codec.rtsp.*;

public class RaopAudioHandler extends SimpleChannelUpstreamHandler {
	private static Logger s_logger = Logger.getLogger(RaopAudioHandler.class.getName());

	static enum RaopRtpChannelType { Audio, Control, Timing };
	
	private static final String HeaderTransport = "Transport";
	private static final String HeaderSession = "Session";

	private class RaopRtpInputToAudioRouterUpstreamHandler extends SimpleChannelUpstreamHandler {
		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent evt)
			throws Exception
		{
			Channel audioChannel = null;
			synchronized(RaopAudioHandler.this) {
				audioChannel = m_audioChannel;
			}
			
			if ((m_audioChannel != null) && m_audioChannel.isOpen() && m_audioChannel.isReadable()) {
				audioChannel.getPipeline().sendUpstream(new UpstreamMessageEvent(
					audioChannel,
					evt.getMessage(),
					evt.getRemoteAddress())
				);
			}
		}
	}
	
	private class RaopRtpAudioToOutputRouterDownstreamHandler extends SimpleChannelDownstreamHandler {
		@Override
		public void writeRequested(ChannelHandlerContext ctx, MessageEvent evt)
			throws Exception
		{
			RaopRtpPacket packet = (RaopRtpPacket)evt.getMessage();
			
			Channel controlChannel = null;
			Channel timingChannel = null;
			synchronized(RaopAudioHandler.this) {
				controlChannel = m_controlChannel;
				timingChannel = m_timingChannel;
			}
			
			if (packet instanceof RaopRtpPacket.RetransmitRequest) {
				if ((controlChannel != null) && controlChannel.isOpen() && controlChannel.isWritable())
					controlChannel.write(evt.getMessage());
			}
			else if (packet instanceof RaopRtpPacket.TimingRequest) {
				if ((timingChannel != null) && timingChannel.isOpen() && timingChannel.isWritable())
					timingChannel.write(evt.getMessage());
			}
			else {
				super.writeRequested(ctx, evt);
			}
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
			
			RaopRtpPacket.Audio audioPacket = (RaopRtpPacket.Audio)evt.getMessage();

			AudioOutputQueue audioOutputQueue;
			synchronized(RaopAudioHandler.this) {
				audioOutputQueue = m_audioOutputQueue;
			}
			
			if (audioOutputQueue != null) {
				byte[] samples = new byte[audioPacket.getPayload().capacity()];
				audioPacket.getPayload().getBytes(0, samples);
				m_audioOutputQueue.enqueue(audioPacket.getTimeStamp(), samples);
				if (s_logger.isLoggable(Level.FINEST))
					s_logger.finest("Packet with sequence " + audioPacket.getSequence() + " for playback at " + audioPacket.getTimeStamp() + " submitted to audio output queue");
			}
			else {
				s_logger.warning("No audio queue available, dropping packet");
			}
			
			super.messageReceived(ctx, evt);
		}
	}
	
	private final Cipher m_rsaPkCS1OaepCipher = AirTunesKeys.getCipher("RSA/None/OAEPWithSHA1AndMGF1Padding", "BC");

	private final ExecutorService m_rtpExecutorService;
	
	private final ChannelHandler m_exceptionLoggingHandler = new ExceptionLoggingHandler();
	private final ChannelHandler m_decodeHandler = new RaopRtpDecodeHandler();
	private final ChannelHandler m_encodeHandler = new RtpEncodeHandler();
	private final ChannelHandler m_packetLoggingHandler = new RtpLoggingHandler();
	private final ChannelHandler m_inputToAudioRouterDownstreamHandler = new RaopRtpInputToAudioRouterUpstreamHandler();
	private final ChannelHandler m_audioToOutputRouterUpstreamHandler = new RaopRtpAudioToOutputRouterDownstreamHandler();
	private ChannelHandler m_decryptionHandler;
	private ChannelHandler m_audioDecodeHandler;
	private ChannelHandler m_resendRequestHandler;
	private ChannelHandler m_timingHandler;
	private final ChannelHandler m_audioEnqueueHandler = new RaopRtpAudioEnqueueHandler();

	private AudioStreamInformationProvider m_audioStreamInformationProvider;
	private AudioOutputQueue m_audioOutputQueue;

	private final ChannelGroup m_rtpChannels = new DefaultChannelGroup();
	private Channel m_audioChannel;
	private Channel m_controlChannel;
	private Channel m_timingChannel;
		
	public RaopAudioHandler(ExecutorService rtpExecutorService) {
		m_rtpExecutorService = rtpExecutorService;
		reset();
	}
	
	private void reset() {
		if (m_audioOutputQueue != null)
			m_audioOutputQueue.close();

		m_rtpChannels.close();

		m_decryptionHandler = null;
		m_audioDecodeHandler = null;
		m_resendRequestHandler = null;
		m_timingHandler = null;
		
		m_audioStreamInformationProvider = null;
		m_audioOutputQueue = null;
		
		m_audioChannel = null;
		m_controlChannel = null;
		m_timingChannel = null;
	}
	
	@Override
	public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent evt)
		throws Exception
	{
		s_logger.info("RTSP connection was shut down, closing RTP channels and audio output queue");
		
		synchronized(this) {
			reset();
		}
		
		super.channelClosed(ctx, evt);
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
	public synchronized void announceReceived(ChannelHandlerContext ctx, HttpRequest req)
		throws Exception
	{
		if (!req.containsHeader("Content-Type"))
			throw new ProtocolException("No Content-Type header");
		if (!"application/sdp".equals(req.getHeader("Content-Type")))
			throw new ProtocolException("Invalid Content-Type header, expected application/sdp but got " + req.getHeader("Content-Type"));
		
		reset();
		
		final String dsp = req.getContent().toString(Charset.forName("ASCII")).replace("\r", "");

		SecretKey aesKey = null;
		IvParameterSpec aesIv = null;
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

						m_rsaPkCS1OaepCipher.init(Cipher.DECRYPT_MODE, AirTunesKeys.PrivateKey);
						aesKeyRaw = m_rsaPkCS1OaepCipher.doFinal(Base64.decodeUnpadded(value));
						
						aesKey = new SecretKeySpec(aesKeyRaw, "AES");
					}
					else if ("aesiv".equals(key)) {
						aesIv = new IvParameterSpec(Base64.decodeUnpadded(value));
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

		if ((aesKey != null) && (aesIv != null))
			m_decryptionHandler = new RaopRtpAudioDecryptionHandler(aesKey, aesIv);
		
		RaopRtpAudioAlacDecodeHandler handler = new RaopRtpAudioAlacDecodeHandler(formatOptions);
		m_audioStreamInformationProvider = handler;
		m_audioDecodeHandler = handler;

		m_audioOutputQueue = new AudioOutputQueue(m_audioStreamInformationProvider);

		m_resendRequestHandler = new RaopRtpRetransmitRequestHandler(m_audioStreamInformationProvider, m_audioOutputQueue);

		m_timingHandler = new RaopRtpTimingHandler(m_audioOutputQueue);

		final HttpResponse response = new DefaultHttpResponse(RtspVersions.RTSP_1_0,  RtspResponseStatuses.OK);
		ctx.getChannel().write(response);
	}
	
	private static Pattern s_pattern_transportOption = Pattern.compile("^([A-Za-z0-9_-]+)(=(.*))?$");
	public synchronized void setupReceived(ChannelHandlerContext ctx, HttpRequest req)
		throws ProtocolException
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

	public synchronized void recordReceived(ChannelHandlerContext ctx, HttpRequest req)
		throws Exception
	{
		if (m_audioStreamInformationProvider == null)
			throw new ProtocolException("Audio stream not configured, cannot start recording");
			
		s_logger.info("Client started streaming");
		
		final HttpResponse response = new DefaultHttpResponse(RtspVersions.RTSP_1_0,  RtspResponseStatuses.OK);
		ctx.getChannel().write(response);
	}
	
	private synchronized void flushReceived(ChannelHandlerContext ctx, HttpRequest req) {
		if (m_audioOutputQueue != null)
			m_audioOutputQueue.flush();

		s_logger.info("Client paused streaming, flushed audio output queue");

		final HttpResponse response = new DefaultHttpResponse(RtspVersions.RTSP_1_0,  RtspResponseStatuses.OK);
		ctx.getChannel().write(response);
	}

	private synchronized void teardownReceived(ChannelHandlerContext ctx, HttpRequest req) {
		final HttpResponse response = new DefaultHttpResponse(RtspVersions.RTSP_1_0,  RtspResponseStatuses.OK);
		ctx.getChannel().setReadable(false);
		ctx.getChannel().write(response).addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				future.getChannel().close();
				s_logger.info("Client stopped streaming, closed RTSP connection");
			}
		});
	}

	private static Pattern s_pattern_parameter = Pattern.compile("^([A-Za-z0-9_-]+): *(.*)$");
	public synchronized void setParameterReceived(ChannelHandlerContext ctx, HttpRequest req)
		throws ProtocolException
	{
		final String body = req.getContent().toString(Charset.forName("ASCII")).replace("\r", "");

		for(final String line: body.split("\n")) {
			try {
				final Matcher m_parameter = s_pattern_parameter.matcher(line);
				if (!m_parameter.matches())
					throw new ProtocolException("Cannot parse line " + line);
				
				final String name = m_parameter.group(1);
				final String value = m_parameter.group(2);
				
				if ("volume".equals(name)) {
					if (m_audioOutputQueue != null)
						m_audioOutputQueue.setGain(Float.parseFloat(value));
					
				}
			}
			catch (Throwable e) {
				throw new ProtocolException("Unable to parse line " + line);
			}
		}
		
		final HttpResponse response = new DefaultHttpResponse(RtspVersions.RTSP_1_0,  RtspResponseStatuses.OK);
		ctx.getChannel().write(response);
	}
	
	public synchronized void getParameterReceived(ChannelHandlerContext ctx, HttpRequest req)
		throws ProtocolException
	{
		StringBuilder body = new StringBuilder();
		
		if (m_audioOutputQueue != null) {
			body.append("volume: ");
			body.append(m_audioOutputQueue.getGain());
			body.append("\r\n");
		}
			
		final HttpResponse response = new DefaultHttpResponse(RtspVersions.RTSP_1_0,  RtspResponseStatuses.OK);
		response.setContent(ChannelBuffers.wrappedBuffer(body.toString().getBytes(Charset.forName("ASCII"))));
		ctx.getChannel().write(response);
	}

	private Channel createRtpChannel(final SocketAddress local, final SocketAddress remote, final RaopRtpChannelType channelType)
	{
		ConnectionlessBootstrap bootstrap = new ConnectionlessBootstrap(new NioDatagramChannelFactory(m_rtpExecutorService));
		bootstrap.setOption("receiveBufferSizePredictorFactory", new FixedReceiveBufferSizePredictorFactory(1500));
		bootstrap.setOption("receiveBufferSize", 1048576);
		bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
			@Override
			public ChannelPipeline getPipeline() throws Exception {
				ChannelPipeline pipeline = Channels.pipeline();
				
				pipeline.addLast("executionHandler", AirReceiver.RtpExecutionHandler);
				pipeline.addLast("exceptionLogger", m_exceptionLoggingHandler);
				pipeline.addLast("decoder", m_decodeHandler);
				pipeline.addLast("encoder", m_encodeHandler);
				if (!channelType.equals(RaopRtpChannelType.Audio)) {
					pipeline.addLast("inputToAudioRouter", m_inputToAudioRouterDownstreamHandler);
					pipeline.addLast("packetLogger", m_packetLoggingHandler);
				}
				else {
					pipeline.addLast("packetLogger", m_packetLoggingHandler);
					pipeline.addLast("audioToOutputRouter", m_audioToOutputRouterUpstreamHandler);
					pipeline.addLast("resendRequester", m_resendRequestHandler);
					if (m_decryptionHandler != null)
						pipeline.addLast("decrypt", m_decryptionHandler);
					if (m_audioDecodeHandler != null)
						pipeline.addLast("audioDecode", m_audioDecodeHandler);
					pipeline.addLast("timing", m_timingHandler);
					pipeline.addLast("enqueue", m_audioEnqueueHandler);
				}
				
				return pipeline;
			}
		});
		
		Channel channel = bootstrap.bind(local);
		m_rtpChannels.add(channel);
		
		if (remote != null)
			channel.connect(remote);
		
		return channel;
	}
	
	private InetSocketAddress substitutePort(final InetSocketAddress address, final int port) {
		return new InetSocketAddress(address.getAddress(), port);
	}
}
