package org.phlo.AirReceiver;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.netty.channel.*;

public class ExceptionLoggingHandler extends SimpleChannelHandler {
	private static Logger s_logger = Logger.getLogger(ExceptionLoggingHandler.class.getName());

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent evt) throws Exception {
		s_logger.log(Level.WARNING, "Handler raised exception", evt.getCause());
	}
}
