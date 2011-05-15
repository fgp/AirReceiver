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
		pipeline.addLast("errorResponse", new RtspErrorResponseHandler());
		pipeline.addLast("challengeResponse", new RaopRtspChallengeResponseHandler(AirReceiver.HardwareAddressBytes));
		pipeline.addLast("header", new RaopRtspHeaderHandler());
		pipeline.addLast("options", new RaopRtspOptionsHandler());
		pipeline.addLast("audio", new RaopAudioHandler(AirReceiver.ExecutorService));
		pipeline.addLast("unsupportedResponse", new RtspUnsupportedResponseHandler());
		
		return pipeline;
	}

}
