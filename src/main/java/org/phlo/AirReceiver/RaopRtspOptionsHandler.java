package org.phlo.AirReceiver;

import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.codec.rtsp.*;

public class RaopRtspOptionsHandler extends SimpleChannelUpstreamHandler {
	private static final String Options =
		RaopRtspMethods.ANNOUNCE.getName() + ", " +
		RaopRtspMethods.SETUP.getName() + ", " +
		RaopRtspMethods.RECORD.getName() + ", " +
		RaopRtspMethods.PAUSE.getName() + ", " +
		RaopRtspMethods.FLUSH.getName() + ", " +
		RtspMethods.TEARDOWN.getName() + ", " +
		RaopRtspMethods.OPTIONS.getName() + ", " +
		RaopRtspMethods.GET_PARAMETER.getName() + ", " +
		RaopRtspMethods.SET_PARAMETER.getName();
	
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent evt) throws Exception {
		HttpRequest req = (HttpRequest)evt.getMessage();
		
		if (RtspMethods.OPTIONS.equals(req.getMethod())) {
	        HttpResponse response = new DefaultHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.OK);
	        response.setHeader(RtspHeaders.Names.PUBLIC, Options);
			ctx.getChannel().write(response);
		}
		else {
			super.messageReceived(ctx, evt);
		}
	}
}
