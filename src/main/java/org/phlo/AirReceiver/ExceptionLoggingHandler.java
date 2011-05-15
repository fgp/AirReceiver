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

import org.jboss.netty.channel.*;

public class ExceptionLoggingHandler extends SimpleChannelHandler {
	private static Logger s_logger = Logger.getLogger(ExceptionLoggingHandler.class.getName());

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent evt) throws Exception {
		super.exceptionCaught(ctx, evt);
		s_logger.log(Level.WARNING, "Handler raised exception", evt.getCause());
	}
}
