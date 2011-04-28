package org.phlo.AirReceiver;

import java.util.logging.Logger;

import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.codec.rtsp.RtspResponseStatuses;
import org.jboss.netty.handler.codec.rtsp.RtspVersions;

public class RtspUnsupportedResponseHandler extends SimpleChannelUpstreamHandler {
	private static Logger s_logger = Logger.getLogger(RtspUnsupportedResponseHandler.class.getName());

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent evt) throws Exception {
		HttpRequest req = (HttpRequest)evt.getMessage();
		
		s_logger.warning("Method " + req.getMethod() + " is not supported");

		HttpResponse response = new DefaultHttpResponse(RtspVersions.RTSP_1_0,  RtspResponseStatuses.METHOD_NOT_VALID);
		ctx.getChannel().write(response);
	}
}
