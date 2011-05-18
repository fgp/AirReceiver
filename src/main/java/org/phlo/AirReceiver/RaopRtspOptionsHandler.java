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
	public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent evt) throws Exception {
		final HttpRequest req = (HttpRequest)evt.getMessage();

		if (RtspMethods.OPTIONS.equals(req.getMethod())) {
	        final HttpResponse response = new DefaultHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.OK);
	        response.setHeader(RtspHeaders.Names.PUBLIC, Options);
			ctx.getChannel().write(response);
		}
		else {
			super.messageReceived(ctx, evt);
		}
	}
}
