package org.phlo.AirReceiver;

import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;

public class RaopRtspHeaderHandler extends SimpleChannelHandler
{
	private static final String HeaderCSeq = "CSeq";
	private static final String HeaderAudioJackStatus = "Audio-Jack-Status";
	private static final String HeaderAudioJackStatusDefault = "connected; type=analog";

	private String m_cseq;
	
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent evt)
		throws Exception
	{
		final HttpRequest req = (HttpRequest)evt.getMessage();

		synchronized(this) {
			if (req.containsHeader(HeaderCSeq)) {
				m_cseq = req.getHeader(HeaderCSeq);
			}
			else {
				throw new ProtocolException("No CSeq header");
			}
		}
		
		super.messageReceived(ctx, evt);
	}
	
	@Override
	public void writeRequested(ChannelHandlerContext ctx, MessageEvent evt)
		throws Exception
	{
		final HttpResponse resp = (HttpResponse)evt.getMessage();
		
		synchronized(this) {
			if (m_cseq != null)
				resp.setHeader(HeaderCSeq, m_cseq);
			
			resp.setHeader(HeaderAudioJackStatus, HeaderAudioJackStatusDefault);
		}
		
		super.writeRequested(ctx, evt);
	}
}
