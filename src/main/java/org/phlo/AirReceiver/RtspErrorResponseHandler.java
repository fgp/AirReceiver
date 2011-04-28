package org.phlo.AirReceiver;

import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.codec.rtsp.*;

public class RtspErrorResponseHandler extends SimpleChannelHandler {
	/**
	 * Prevents an infinite loop that otherwise occurs if
	 * write()ing the exception response itself triggers
	 * an exception (which we will then attempt to write(),
	 * triggering the same exception, ...)
	 * We avoid that loop by dropping all exception events
	 * after the first one.
	 */
	private boolean m_messageTriggeredException = false;
	
	@Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent evt) throws Exception {
		m_messageTriggeredException = false;
		
		super.messageReceived(ctx, evt);
    }
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent evt) throws Exception {
		if (m_messageTriggeredException)
			return;
		m_messageTriggeredException = true;
		
		if (ctx.getChannel().isConnected()) {
			HttpResponse response = new DefaultHttpResponse(RtspVersions.RTSP_1_0,  RtspResponseStatuses.INTERNAL_SERVER_ERROR);
			ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
		}
	}
}
