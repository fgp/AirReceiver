package org.phlo.AirReceiver;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

public class RaopRtpAudioDecryptionHandler extends SimpleChannelUpstreamHandler {
	private final Cipher m_aesCipher = AirTunesKeys.getCipher("AES/CBC/NoPadding", "BC");
	private final SecretKey m_aesKey;
	private final IvParameterSpec m_aesIv;

	public RaopRtpAudioDecryptionHandler(final SecretKey aesKey, final IvParameterSpec aesIv) {
		m_aesKey = aesKey;
		m_aesIv = aesIv;
	}
	
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent evt)
		throws Exception
	{
		if (evt.getMessage() instanceof RaopRtpPacket.Audio) {
			RaopRtpPacket.Audio audioPacket = (RaopRtpPacket.Audio)evt.getMessage();
			ChannelBuffer audioPayload = audioPacket.getPayload();
			
			m_aesCipher.init(Cipher.DECRYPT_MODE, m_aesKey, m_aesIv);
			for(int i=0; (i + 16) <= audioPayload.capacity(); i += 16) {
				byte[] block = new byte[16];
				audioPayload.getBytes(i, block);
				block = m_aesCipher.update(block);
				audioPayload.setBytes(i, block);
			}
		}
		
		super.messageReceived(ctx, evt);
	}
}
