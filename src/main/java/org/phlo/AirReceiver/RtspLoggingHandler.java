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

import java.nio.charset.Charset;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;

/**
 * Logs RTSP requests and responses.
 */
public class RtspLoggingHandler extends SimpleChannelHandler
{
	private static final Logger s_logger = Logger.getLogger(RtspLoggingHandler.class.getName());

	@Override
	public void channelConnected(final ChannelHandlerContext ctx, final ChannelStateEvent e)
		throws Exception
	{
		s_logger.info("Client " + e.getChannel().getRemoteAddress() + " connected on " + e.getChannel().getLocalAddress());
	}

	@Override
	public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent evt)
		throws Exception
	{
		final HttpRequest req = (HttpRequest)evt.getMessage();

		final Level level = Level.FINE;
		if (s_logger.isLoggable(level)) {
			final String content = req.getContent().toString(Charset.defaultCharset());

			final StringBuilder s = new StringBuilder();
			s.append(">");
			s.append(req.getMethod());
			s.append(" ");
			s.append(req.getUri());
			s.append("\n");
			for(final Map.Entry<String, String> header: req.getHeaders()) {
				s.append("  ");
				s.append(header.getKey());
				s.append(": ");
				s.append(header.getValue());
				s.append("\n");
			}
			s.append(content);
			s_logger.log(Level.FINE, s.toString());
		}

		super.messageReceived(ctx, evt);
	}

	@Override
	public void writeRequested(final ChannelHandlerContext ctx, final MessageEvent evt)
		throws Exception
	{
		final HttpResponse resp = (HttpResponse)evt.getMessage();

		final Level level = Level.FINE;
		if (s_logger.isLoggable(level)) {
			final StringBuilder s = new StringBuilder();
			s.append("<");
			s.append(resp.getStatus().getCode());
			s.append(" ");
			s.append(resp.getStatus().getReasonPhrase());
			s.append("\n");
			for(final Map.Entry<String, String> header: resp.getHeaders()) {
				s.append("  ");
				s.append(header.getKey());
				s.append(": ");
				s.append(header.getValue());
				s.append("\n");
			}
			s_logger.log(Level.FINE, s.toString());
		}

		super.writeRequested(ctx, evt);
	}
}
