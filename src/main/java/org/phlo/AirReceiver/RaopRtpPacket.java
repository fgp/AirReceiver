package org.phlo.AirReceiver;

import org.jboss.netty.buffer.ChannelBuffer;

public abstract class RaopRtpPacket extends RtpPacket {
	public static long getBeUInt(ChannelBuffer buffer, int index) {
		return (long)(
			((buffer.getByte(index+0) & 0xffL) << 24) |
			((buffer.getByte(index+1) & 0xffL) << 16) |
			((buffer.getByte(index+2) & 0xffL) << 8) |
			((buffer.getByte(index+3) & 0xffL) << 0)
		);
	}
	
	public static void setBeUInt(ChannelBuffer buffer, int index, long value) {
		assert (value & ~0xffffffffL) == 0;
		buffer.setByte(index+0, (int)((value & 0xff000000L) >> 24));
		buffer.setByte(index+1, (int)((value & 0x00ff0000L) >> 16));
		buffer.setByte(index+2, (int)((value & 0x0000ff00L) >> 8));
		buffer.setByte(index+3, (int)((value & 0x000000ffL) >> 0));
	}

	public static int getBeUInt16(ChannelBuffer buffer, int index) {
		return (int)(
			((buffer.getByte(index+0) & 0xffL) << 8) |
			((buffer.getByte(index+1) & 0xffL) << 0)
		);
	}
	
	public static void setBeUInt16(ChannelBuffer buffer, int index, int value) {
		assert (value & ~0xffffL) == 0;
		buffer.setByte(index+0, (int)((value & 0xff00L) >> 8));
		buffer.setByte(index+1, (int)((value & 0x00ffL) >> 0));
	}
	
	public static class NtpTime {
		public static final int Length = 8;
		
		private final ChannelBuffer m_buffer;
		
		protected NtpTime(ChannelBuffer buffer) {
			assert buffer.capacity() == Length;
			m_buffer = buffer;
		}
		
		public long getSeconds() {
			return getBeUInt(m_buffer, 0);
		}
		
		public void setSeconds(long seconds) {
			setBeUInt(m_buffer, 0, seconds);
		}
		
		public long getFraction() {
			return getBeUInt(m_buffer, 4);
		}
		
		public void setFraction(long fraction) {
			setBeUInt(m_buffer, 4, fraction);
		}
	}
	
	public static class Timing extends RaopRtpPacket {
		public static final int Length = 32;
		
		protected Timing() {
			super(Length);
		}
		
		protected Timing(ChannelBuffer buffer) {
			super(buffer);
		}
	}
	
	public static class TimingRequest extends Timing {
		public static final byte PayloadType = 0x52;
		
		public TimingRequest() {
			setPayloadType(PayloadType);
		}
		
		protected TimingRequest(ChannelBuffer buffer) {
			super(buffer);
		}
	}

	public static class TimingResponse extends Timing {
		public static final byte PayloadType = 0x53;

		public TimingResponse() {
			setPayloadType(PayloadType);
		}
		
		protected TimingResponse(ChannelBuffer buffer) {
			super(buffer);
		}
	}

	public static class Sync extends RaopRtpPacket {
		public static final byte PayloadType = 0x54;
		public static final int Length = 20;
		
		public Sync() {
			super(Length);
			setPayloadType(PayloadType);
		}
		
		protected Sync(ChannelBuffer buffer) {
			super(buffer);
		}

		public long getNowMinusLatency() {
			return getBeUInt(getBuffer(), RaopRtpPacket.Length);
		}

		public void setNowMinusLatency(long value) {
			setBeUInt(getBuffer(), RaopRtpPacket.Length, value);
		}

		public NtpTime getTimeLastSync() {
			return new NtpTime(getBuffer().slice(RaopRtpPacket.Length + 4, 8));
		}
		
		public long getNow() {
			return getBeUInt(getBuffer(), RaopRtpPacket.Length + 4 + 8);
		}

		public void setNow(long value) {
			setBeUInt(getBuffer(), RaopRtpPacket.Length + 4 + 8, value);
		}
		
		@Override
		public String toString() {
			StringBuilder s = new StringBuilder();
			s.append(super.toString());
			
			s.append(" "); s.append("now-lat="); s.append(getNowMinusLatency());
			s.append(" "); s.append("last.sec="); s.append(getTimeLastSync().getSeconds());
			s.append(" "); s.append("last.frac="); s.append(getTimeLastSync().getFraction());
			s.append(" "); s.append("now="); s.append(getNow());
			
			return s.toString();
		}
	}

	public static class RetransmitRequest extends RaopRtpPacket {
		public static final byte PayloadType = 0x55;
		public static final int Length = 8;
		
		public RetransmitRequest() {
			super(Length);
			setPayloadType(PayloadType);
			setMarker(true);
			setSequence(1);
		}
		
		protected RetransmitRequest(ChannelBuffer buffer) {
			super(buffer);
		}
		
		public long getSequenceFirst() {
			return getBeUInt16(getBuffer(), RaopRtpPacket.Length);
		}

		public void setSequenceFirst(int value) {
			setBeUInt16(getBuffer(), RaopRtpPacket.Length, value);
		}

		public long getSequenceCount() {
			return getBeUInt16(getBuffer(), RaopRtpPacket.Length + 2);
		}

		public void setSequenceCount(int value) {
			setBeUInt16(getBuffer(), RaopRtpPacket.Length + 2, value);
		}
		
		@Override
		public String toString() {
			StringBuilder s = new StringBuilder();
			s.append(super.toString());
			
			s.append(" "); s.append("first="); s.append(getSequenceFirst());
			s.append(" "); s.append("count="); s.append(getSequenceCount());
			
			s.append(" [");
			for(int i=0; i < getBuffer().capacity(); ++i) {
				if (i > 0)
					s.append(" ");
				s.append(Integer.toHexString(getBuffer().getByte(i) & 0xff));
			}
			s.append("]");
			
			return s.toString();
		}
	}

	public static class Audio extends RaopRtpPacket {
		public static final byte PayloadType = 0x60;
		public static final int HeaderLength = RaopRtpPacket.Length + 4 + 4;
		
		public Audio(int payloadLength) {
			super(HeaderLength + payloadLength);
			assert payloadLength >= 0;

			setPayloadType(PayloadType);
		}
		
		protected Audio(ChannelBuffer buffer) {
			super(buffer);
		}
		
		public long getTimeStamp() {
			return getBeUInt(getBuffer(), RaopRtpPacket.Length);
		}
		
		public void setTimeStamp(long timeStamp) {
			setBeUInt(getBuffer(), RaopRtpPacket.Length, timeStamp);
		}
		
		public long getSSrc() {
			return getBeUInt(getBuffer(), RaopRtpPacket.Length + 4);
		}
		
		public void setSSrc(long sSrc) {
			setBeUInt(getBuffer(), RaopRtpPacket.Length + 4, sSrc);
		}
		
		public ChannelBuffer getPayload() {
			return getBuffer().slice(HeaderLength, getLength() - HeaderLength);
		}

		@Override
		public String toString() {
			StringBuilder s = new StringBuilder();
			s.append(super.toString());
			
			s.append(" "); s.append("ts="); s.append(getTimeStamp());
			s.append(" "); s.append("ssrc="); s.append(getSSrc());
			s.append(" "); s.append("<"); s.append(getPayload().capacity()); s.append(" bytes payload>");
			
			return s.toString();
		}
	}
	
	public static class AudioRetransmit extends Audio {
		public static final byte PayloadType = 0x56;
		
		public AudioRetransmit(int payloadLength) {
			super(payloadLength);
			setPayloadType(PayloadType);
		}
		
		protected AudioRetransmit(ChannelBuffer buffer) {
			super(buffer);
		}
	}

	public static RaopRtpPacket decode(ChannelBuffer buffer)
		throws ProtocolException
	{
		RtpPacket rtpPacket = new RtpPacket(buffer);
		
		switch (rtpPacket.getPayloadType()) {
			case TimingRequest.PayloadType: return new TimingRequest(buffer);
			case TimingResponse.PayloadType: return new TimingResponse(buffer);
			case Sync.PayloadType: return new Sync(buffer);
			case RetransmitRequest.PayloadType: return new RetransmitRequest(buffer);
			case AudioRetransmit.PayloadType: return new AudioRetransmit(buffer);
			case Audio.PayloadType: return new Audio(buffer);
			default: throw new ProtocolException("Invalid PayloadType " + rtpPacket.getPayloadType());
		}
	}
	
	protected RaopRtpPacket(int length) {
		super(length);
		setVersion((byte)2);
	}
	
	protected RaopRtpPacket(ChannelBuffer buffer) {
		super(buffer);
	}
}
