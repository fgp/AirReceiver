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

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import org.jboss.netty.buffer.*;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;

public class RaopRtpAudioDecryptionHandler extends OneToOneDecoder {
	private final Cipher m_aesCipher = AirTunesKeys.getCipher("AES/CBC/NoPadding", "BC");
	private final SecretKey m_aesKey;
	private final IvParameterSpec m_aesIv;

	public RaopRtpAudioDecryptionHandler(final SecretKey aesKey, final IvParameterSpec aesIv) {
		m_aesKey = aesKey;
		m_aesIv = aesIv;
	}
	
	@Override
	protected synchronized Object decode(ChannelHandlerContext ctx, Channel channel, Object msg)
		throws Exception
	{
		if (msg instanceof RaopRtpPacket.Audio) {
			RaopRtpPacket.Audio audioPacket = (RaopRtpPacket.Audio)msg;
			ChannelBuffer audioPayload = audioPacket.getPayload();
			
			synchronized(this) {
				m_aesCipher.init(Cipher.DECRYPT_MODE, m_aesKey, m_aesIv);
				for(int i=0; (i + 16) <= audioPayload.capacity(); i += 16) {
					byte[] block = new byte[16];
					audioPayload.getBytes(i, block);
					block = m_aesCipher.update(block);
					audioPayload.setBytes(i, block);
				}
			}
		}
		
		return msg;
	}
}
