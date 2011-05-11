package org.phlo.AirReceiver;

import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.rtsp.*;

public class RaopRtspPipelineFactory implements ChannelPipelineFactory {
	@Override
	public ChannelPipeline getPipeline() throws Exception {
		ChannelPipeline pipeline = Channels.pipeline();
		
		pipeline.addLast("closeOnShutdownHandler", AirReceiver.CloseOnShutdownHandler);
		pipeline.addLast("exceptionLogger", new ExceptionLoggingHandler());
		pipeline.addLast("decoder", new RtspRequestDecoder());
		pipeline.addLast("encoder", new RtspResponseEncoder());
		pipeline.addLast("logger", new RtspLoggingHandler());
		pipeline.addLast("challengeResponse", new RaopRtspChallengeResponseHandler(new byte[] {(byte)0x00, (byte)0xDE, (byte)0xAD, (byte)0xBE, (byte)0xEF, (byte)0x00}));
		pipeline.addLast("errorResponse", new RtspErrorResponseHandler());
		pipeline.addLast("header", new RaopRtspHeaderHandler());
		pipeline.addLast("options", new RaopRtspOptionsHandler());
		pipeline.addLast("audio", new RaopAudioHandler(AirReceiver.ExecutorService));
		pipeline.addLast("unsupportedResponse", new RtspUnsupportedResponseHandler());
		
		return pipeline;
	}

}
