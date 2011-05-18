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

import org.jboss.netty.buffer.*;

public class RtpPacket {
	public static final int Length = 4;

	final private ChannelBuffer m_buffer;

	protected RtpPacket(final int size) {
		assert size >= Length;
		m_buffer = ChannelBuffers.buffer(size);
		m_buffer.writeZero(size);
		setVersion((byte)2);
	}

	public RtpPacket(final ChannelBuffer buffer) throws ProtocolException {
		m_buffer = buffer;
	}

	public RtpPacket(final ChannelBuffer buffer, final int minimumSize) throws ProtocolException {
		this(buffer);
		if (buffer.capacity() < minimumSize)
			throw new InvalidPacketException("Packet had invalid size " + buffer.capacity() + " instead of at least " + minimumSize);
	}

	public ChannelBuffer getBuffer() {
		return m_buffer;
	}

	public int getLength() {
		return m_buffer.capacity();
	}

	public byte getVersion() {
		return (byte)((m_buffer.getByte(0) & (0xC0)) >> 6);
	}

	public void setVersion(final byte version) {
		assert (version & ~0x03) == 0;
		m_buffer.setByte(0, (m_buffer.getByte(0) & ~(0xC0)) | (version << 6));
	}

	public boolean getPadding() {
		return (m_buffer.getByte(0) & (0x20)) != 0;
	}

	public void setPadding(final boolean padding) {
		m_buffer.setByte(0, (m_buffer.getByte(0) & ~0x20) | (padding ? 0x20 : 0x00));
	}

	public boolean getExtension() {
		return (m_buffer.getByte(0) & (0x10)) != 0;
	}

	public void setExtension(final boolean extension) {
		m_buffer.setByte(0, (m_buffer.getByte(0) & ~0x10) | (extension ? 0x10 : 0x00));
	}

	public byte getCsrcCount() {
		return (byte)(m_buffer.getByte(0) & (0x0f));
	}

	public void setCsrcCount(final byte csrcCount) {
		assert (csrcCount & ~0x0f) == 0;
		m_buffer.setByte(0, (m_buffer.getByte(0) & ~0x0f) | csrcCount);
	}

	public boolean getMarker() {
		return (m_buffer.getByte(1) & (0x80)) != 0;
	}

	public void setMarker(final boolean marker) {
		m_buffer.setByte(1, (m_buffer.getByte(1) & ~0x80) | (marker ? 0x80 : 0x00));
	}

	public byte getPayloadType() {
		return (byte)(m_buffer.getByte(1) & (0x7f));
	}

	public void setPayloadType(final byte payloadType) {
		assert (payloadType & ~0x7f) == 0;
		m_buffer.setByte(1, (m_buffer.getByte(1) & ~0x7f) | payloadType);
	}

	public int getSequence() {
		return (
			((m_buffer.getByte(2) & 0xff) << 8) |
			((m_buffer.getByte(3) & 0xff) << 0)
		);
	}

	public void setSequence(final int sequence) {
		assert (sequence & ~0xffff) == 0;
		m_buffer.setByte(2, (sequence & 0xff00) >> 8);
		m_buffer.setByte(3, (sequence & 0x00ff) >> 0);
	}

	@Override
	public String toString() {
		final StringBuilder s = new StringBuilder();

		s.append(this.getClass().getSimpleName());
		s.append("(");
		s.append(Integer.toHexString(getPayloadType()));
		s.append(")");

		s.append(" "); s.append("ver="); s.append(getVersion());
		s.append(" "); s.append("pad="); s.append(getPadding());
		s.append(" "); s.append("ext="); s.append(getExtension());
		s.append(" "); s.append("csrcc="); s.append(getCsrcCount());
		s.append(" "); s.append("marker="); s.append(getMarker());
		s.append(" "); s.append("seq="); s.append(getSequence());

		return s.toString();
	}
}
