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
