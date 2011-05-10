package org.phlo.AirReceiver;

import org.jboss.netty.buffer.*;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;

public class RaopRtpDecodeHandler extends OneToOneDecoder {
	@Override
	protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg)
		throws Exception
	{
		if (msg instanceof ChannelBuffer)
			return RaopRtpPacket.decode((ChannelBuffer)msg);
		else
			return msg;
	}
}
