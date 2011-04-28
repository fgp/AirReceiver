package org.phlo.AirReceiver;

import java.net.*;
import java.nio.ByteBuffer;

import javax.crypto.*;

import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;

public class RaopRtspChallengeResponseHandler extends SimpleChannelHandler
{
	private static final String HeaderChallenge = "Apple-Challenge";
	private static final String HeaderSignature = "Apple-Response";
	
	private final byte[] m_hwAddress;
	private final Cipher m_rsaPkCS1PaddingCipher = AirTunesKeys.getCipher("RSA/None/PKCS1Padding", "BC");
	
	private byte[] m_challenge;
	private InetAddress m_localAddress;
	
	public RaopRtspChallengeResponseHandler(final byte[] hwAddress) {
		assert hwAddress.length == 6;

		m_hwAddress = hwAddress;
	}
	
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent evt)
		throws Exception
	{
		final HttpRequest req = (HttpRequest)evt.getMessage();

		if (req.containsHeader(HeaderChallenge)) {
			/* The challenge is sent without padding! */
			final byte[] challenge = Base64.decodeUnpadded(req.getHeader(HeaderChallenge));
			
			/* Verify that we got 16 bytes */
			if (challenge.length != 16)
				throw new ProtocolException("Invalid Apple-Challenge header, " + challenge.length + " instead of 16 bytes");
			
			/* Remember challenge and local address.
			 * Both are required to compute the response
			 */
			m_challenge = challenge;
			m_localAddress = ((InetSocketAddress)ctx.getChannel().getLocalAddress()).getAddress();
		}
		else {
			/* Forget last challenge */
			m_challenge = null;
			m_localAddress = null;
		}
		
		super.messageReceived(ctx, evt);
	}
	
	@Override
	public void writeRequested(ChannelHandlerContext ctx, MessageEvent evt)
		throws Exception
	{
		final HttpResponse resp = (HttpResponse)evt.getMessage();
		
		if (m_challenge != null) {
			/* Get appropriate response to challenge and
			 * add to the response base-64 encoded. XXX
			 */
			String sig = Base64.encodePadded(getSignature());
			
			resp.setHeader(HeaderSignature, sig);
		}
		
		super.writeRequested(ctx, evt);
	}
	
	private byte[] getSignature() {
		final ByteBuffer sigData = ByteBuffer.allocate(0x20);
		
		sigData.put(m_challenge);
		sigData.put(m_localAddress.getAddress());
		sigData.put(m_hwAddress);
		while (sigData.hasRemaining())
			sigData.put((byte)0);
		
		try {
			synchronized(m_rsaPkCS1PaddingCipher) {
				m_rsaPkCS1PaddingCipher.init(Cipher.ENCRYPT_MODE, AirTunesKeys.PrivateKey);
				return m_rsaPkCS1PaddingCipher.doFinal(sigData.array());
			}
		}
		catch (Exception e) {
			throw new RuntimeException("Unable to sign response", e);
		}
	}
}