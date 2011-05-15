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
