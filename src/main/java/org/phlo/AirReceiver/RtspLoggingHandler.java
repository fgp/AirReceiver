package org.phlo.AirReceiver;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;

public class RtspLoggingHandler extends SimpleChannelHandler
{
	private static final Logger s_logger = Logger.getLogger(RtspLoggingHandler.class.getName());

	public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
		throws Exception
	{
		s_logger.info("Client " + e.getChannel().getRemoteAddress() + " connected on " + e.getChannel().getLocalAddress());
	}
	
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent evt)
		throws Exception
	{
		HttpRequest req = (HttpRequest)evt.getMessage();
		
		Level level = Level.FINE;
		if (s_logger.isLoggable(level)) {
			String content = req.getContent().toString(Charset.defaultCharset());
			
			StringBuilder s = new StringBuilder();
			s.append(">");
			s.append(req.getMethod());
			s.append(" ");
			s.append(req.getUri());
			s.append("\n");
			for(Map.Entry<String, String> header: req.getHeaders()) {
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
	public void writeRequested(ChannelHandlerContext ctx, MessageEvent evt)
		throws Exception
	{
		HttpResponse resp = (HttpResponse)evt.getMessage();
		
		Level level = Level.FINE;
		if (s_logger.isLoggable(level)) {
			StringBuilder s = new StringBuilder();
			s.append("<");
			s.append(resp.getStatus().getCode());
			s.append(" ");
			s.append(resp.getStatus().getReasonPhrase());
			s.append("\n");
			for(Map.Entry<String, String> header: resp.getHeaders()) {
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
