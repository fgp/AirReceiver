package org.phlo.AirReceiver;

import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;

public class RaopRtspHeaderHandler extends SimpleChannelHandler
{
	//private static final String HeaderClientInstance = "Client-Instance";
	private static final String HeaderCSeq = "CSeq";
	private static final String HeaderAudioJackStatus = "Audio-Jack-Status";
	private static final String HeaderAudioJackStatusDefault = "connected; type=analog";

	//private String m_clientInstance;
	private String m_cseq;
	
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent evt)
		throws Exception
	{
		final HttpRequest req = (HttpRequest)evt.getMessage();

		/* iOS doesn't send this
		if (req.containsHeader(HeaderClientInstance)) {
			final String reqClientInstance = req.getHeader(HeaderClientInstance);

			if (m_clientInstance == null) {
				m_clientInstance = reqClientInstance;
			}
			else {
				if (!m_clientInstance.equals(reqClientInstance))
					throw new ProtocolException("Invalid Client-Instance header");
			}
		}
		else {
			throw new ProtocolException("No Client-Instance header");
		}
		*/
		
		if (req.containsHeader(HeaderCSeq)) {
			m_cseq = req.getHeader(HeaderCSeq);
		}
		else {
			throw new ProtocolException("No CSeq header");
		}
		
		super.messageReceived(ctx, evt);
	}
	
	@Override
	public void writeRequested(ChannelHandlerContext ctx, MessageEvent evt)
		throws Exception
	{
		final HttpResponse resp = (HttpResponse)evt.getMessage();
		
		if (m_cseq != null)
			resp.setHeader(HeaderCSeq, m_cseq);
		
		resp.setHeader(HeaderAudioJackStatus, HeaderAudioJackStatusDefault);
		
		super.writeRequested(ctx, evt);
	}
}
