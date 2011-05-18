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

import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

public class RtpLoggingHandler extends SimpleChannelHandler {
	private static final Logger s_logger = Logger.getLogger(RtpLoggingHandler.class.getName());

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent evt)
		throws Exception
	{
		if (evt.getMessage() instanceof RtpPacket) {
			RtpPacket packet = (RtpPacket)evt.getMessage();
			
			Level level = getPacketLevel(packet);
			if (s_logger.isLoggable(level))
				s_logger.log(level, evt.getRemoteAddress() + "> " + packet.toString());
		}
		
		super.messageReceived(ctx, evt);
	}
	
	@Override
	public void writeRequested(ChannelHandlerContext ctx, MessageEvent evt)
		throws Exception
	{
		if (evt.getMessage() instanceof RtpPacket) {
			RtpPacket packet = (RtpPacket)evt.getMessage();
			
			Level level = getPacketLevel(packet);
			if (s_logger.isLoggable(level))
				s_logger.log(level, evt.getRemoteAddress() + "< " + packet.toString());
		}
		
		super.writeRequested(ctx, evt);
	}
	
	private Level getPacketLevel(final RtpPacket packet) {
		if (packet instanceof RaopRtpPacket.Audio)
			return Level.FINEST;
		else if (packet instanceof RaopRtpPacket.RetransmitRequest)
			return Level.FINEST;
		else if (packet instanceof RaopRtpPacket.Timing)
			return Level.FINEST;
		else
			return Level.FINE;
	}
}
