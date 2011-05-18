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
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent evt) throws Exception {
		synchronized(this) {
			m_messageTriggeredException = false;
		}

		super.messageReceived(ctx, evt);
    }

	@Override
	public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent evt) throws Exception {
		synchronized(this) {
			if (m_messageTriggeredException)
				return;
			m_messageTriggeredException = true;
		}

		if (ctx.getChannel().isConnected()) {
			final HttpResponse response = new DefaultHttpResponse(RtspVersions.RTSP_1_0,  RtspResponseStatuses.INTERNAL_SERVER_ERROR);
			ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
		}
	}
}
