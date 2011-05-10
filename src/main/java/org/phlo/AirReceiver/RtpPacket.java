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
	
	public RtpPacket(final ChannelBuffer buffer) {
		assert buffer.capacity() >= Length;
		m_buffer = buffer;
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
	
	public void setVersion(byte version) {
		assert (version & ~0x03) == 0;
		m_buffer.setByte(0, (m_buffer.getByte(0) & ~(0xC0)) | (version << 6));
	}

	public boolean getPadding() {
		return (m_buffer.getByte(0) & (0x20)) != 0;
	}

	public void setPadding(boolean padding) {
		m_buffer.setByte(0, (m_buffer.getByte(0) & ~0x20) | (padding ? 0x20 : 0x00));
	}
	
	public boolean getExtension() {
		return (m_buffer.getByte(0) & (0x10)) != 0;
	}

	public void setExtension(boolean extension) {
		m_buffer.setByte(0, (m_buffer.getByte(0) & ~0x10) | (extension ? 0x10 : 0x00));
	}

	public byte getCsrcCount() {
		return (byte)(m_buffer.getByte(0) & (0x0f));
	}

	public void setCsrcCount(byte csrcCount) {
		assert (csrcCount & ~0x0f) == 0;
		m_buffer.setByte(0, (m_buffer.getByte(0) & ~0x0f) | csrcCount);
	}

	public boolean getMarker() {
		return (m_buffer.getByte(1) & (0x80)) != 0;
	}
	
	public void setMarker(boolean marker) {
		m_buffer.setByte(1, (m_buffer.getByte(1) & ~0x80) | (marker ? 0x80 : 0x00));
	}

	public byte getPayloadType() {
		return (byte)(m_buffer.getByte(1) & (0x7f));
	}
	
	public void setPayloadType(byte payloadType) {
		assert (payloadType & ~0x7f) == 0;
		m_buffer.setByte(1, (m_buffer.getByte(1) & ~0x7f) | payloadType);
	}
	
	public int getSequence() {
		return (int)(
			((m_buffer.getByte(2) & 0xff) << 8) |
			((m_buffer.getByte(3) & 0xff) << 0)
		);
	}
	
	public void setSequence(int sequence) {
		assert (sequence & ~0xffff) == 0;
		m_buffer.setByte(2, (sequence & 0xff00) >> 8);
		m_buffer.setByte(3, (sequence & 0x00ff) >> 0);
	}
	
	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		
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
