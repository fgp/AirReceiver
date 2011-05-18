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

import org.jboss.netty.buffer.ChannelBuffer;

public abstract class RaopRtpPacket extends RtpPacket {
	public static long getBeUInt(final ChannelBuffer buffer, final int index) {
		return (
			((buffer.getByte(index+0) & 0xffL) << 24) |
			((buffer.getByte(index+1) & 0xffL) << 16) |
			((buffer.getByte(index+2) & 0xffL) << 8) |
			((buffer.getByte(index+3) & 0xffL) << 0)
		);
	}

	public static void setBeUInt(final ChannelBuffer buffer, final int index, final long value) {
		assert (value & ~0xffffffffL) == 0;
		buffer.setByte(index+0, (int)((value & 0xff000000L) >> 24));
		buffer.setByte(index+1, (int)((value & 0x00ff0000L) >> 16));
		buffer.setByte(index+2, (int)((value & 0x0000ff00L) >> 8));
		buffer.setByte(index+3, (int)((value & 0x000000ffL) >> 0));
	}

	public static int getBeUInt16(final ChannelBuffer buffer, final int index) {
		return (int)(
			((buffer.getByte(index+0) & 0xffL) << 8) |
			((buffer.getByte(index+1) & 0xffL) << 0)
		);
	}

	public static void setBeUInt16(final ChannelBuffer buffer, final int index, final int value) {
		assert (value & ~0xffffL) == 0;
		buffer.setByte(index+0, (int)((value & 0xff00L) >> 8));
		buffer.setByte(index+1, (int)((value & 0x00ffL) >> 0));
	}

	public static final class NtpTime {
		public static final int Length = 8;

		private final ChannelBuffer m_buffer;

		protected NtpTime(final ChannelBuffer buffer) {
			assert buffer.capacity() == Length;
			m_buffer = buffer;
		}

		public long getSeconds() {
			return getBeUInt(m_buffer, 0);
		}

		public void setSeconds(final long seconds) {
			setBeUInt(m_buffer, 0, seconds);
		}

		public long getFraction() {
			return getBeUInt(m_buffer, 4);
		}

		public void setFraction(final long fraction) {
			setBeUInt(m_buffer, 4, fraction);
		}

		public double getDouble() {
			return getSeconds() + (double)getFraction() / 0x100000000L;
		}

		public void setDouble(final double v) {
			setSeconds((long)v);
			setFraction((long)(0x100000000L * (v - Math.floor(v))));
		}
	}

	public static class Timing extends RaopRtpPacket {
		public static final int Length = RaopRtpPacket.Length + 4 + 8 + 8 + 8;

		protected Timing() {
			super(Length);
			setMarker(true);
			setSequence(7);
		}

		protected Timing(final ChannelBuffer buffer, final int minimumSize) throws ProtocolException {
			super(buffer, minimumSize);
		}

		public NtpTime getReferenceTime() {
			return new NtpTime(getBuffer().slice(RaopRtpPacket.Length + 4, 8));
		}

		public NtpTime getReceivedTime() {
			return new NtpTime(getBuffer().slice(RaopRtpPacket.Length + 12, 8));
		}

		public NtpTime getSendTime() {
			return new NtpTime(getBuffer().slice(RaopRtpPacket.Length + 20, 8));
		}

		@Override
		public String toString() {
			final StringBuilder s = new StringBuilder();
			s.append(super.toString());

			s.append(" "); s.append("ref="); s.append(getReferenceTime().getDouble());
			s.append(" "); s.append("recv="); s.append(getReceivedTime().getDouble());
			s.append(" "); s.append("send="); s.append(getSendTime().getDouble());

			return s.toString();
		}
	}

	/**
	 * Time synchronization request.
	 *
	 * The sequence number must always be 7, otherwise
	 * at least iOS ignores the packet.
	 */
	public static final class TimingRequest extends Timing {
		public static final byte PayloadType = 0x52;

		public TimingRequest() {
			setPayloadType(PayloadType);
		}

		protected TimingRequest(final ChannelBuffer buffer) throws ProtocolException {
			super(buffer, Length);
		}
	}

	/**
	 * Time synchronization response.
	 *
	 * The sequence should match the request's
	 * sequence, which is always 7.
	 */
	public static final class TimingResponse extends Timing {
		public static final byte PayloadType = 0x53;

		public TimingResponse() {
			setPayloadType(PayloadType);
		}

		protected TimingResponse(final ChannelBuffer buffer) throws ProtocolException {
			super(buffer, Length);
		}
	}

	public static final class Sync extends RaopRtpPacket {
		public static final byte PayloadType = 0x54;
		public static final int Length = RaopRtpPacket.Length + 4 + 8 + 4;

		public Sync() {
			super(Length);
			setPayloadType(PayloadType);
		}

		protected Sync(final ChannelBuffer buffer) throws ProtocolException {
			super(buffer, Length);
		}

		public long getTimeStampMinusLatency() {
			return getBeUInt(getBuffer(), RaopRtpPacket.Length);
		}

		public void setTimeStampMinusLatency(final long value) {
			setBeUInt(getBuffer(), RaopRtpPacket.Length, value);
		}

		public NtpTime getTime() {
			return new NtpTime(getBuffer().slice(RaopRtpPacket.Length + 4, 8));
		}

		public long getTimeStamp() {
			return getBeUInt(getBuffer(), RaopRtpPacket.Length + 4 + 8);
		}

		public void setTimeStamp(final long value) {
			setBeUInt(getBuffer(), RaopRtpPacket.Length + 4 + 8, value);
		}

		@Override
		public String toString() {
			final StringBuilder s = new StringBuilder();
			s.append(super.toString());

			s.append(" "); s.append("ts-lat="); s.append(getTimeStampMinusLatency());
			s.append(" "); s.append("ts="); s.append(getTimeStamp());
			s.append(" "); s.append("time="); s.append(getTime().getDouble());

			return s.toString();
		}
	}

	public static final class RetransmitRequest extends RaopRtpPacket {
		public static final byte PayloadType = 0x55;
		public static final int Length = RaopRtpPacket.Length + 4;

		public RetransmitRequest() {
			super(Length);
			setPayloadType(PayloadType);
			setMarker(true);
			setSequence(1);
		}

		protected RetransmitRequest(final ChannelBuffer buffer) throws ProtocolException {
			super(buffer, Length);
		}

		public int getSequenceFirst() {
			return getBeUInt16(getBuffer(), RaopRtpPacket.Length);
		}

		public void setSequenceFirst(final int value) {
			setBeUInt16(getBuffer(), RaopRtpPacket.Length, value);
		}

		public int getSequenceCount() {
			return getBeUInt16(getBuffer(), RaopRtpPacket.Length + 2);
		}

		public void setSequenceCount(final int value) {
			setBeUInt16(getBuffer(), RaopRtpPacket.Length + 2, value);
		}

		@Override
		public String toString() {
			final StringBuilder s = new StringBuilder();
			s.append(super.toString());

			s.append(" "); s.append("first="); s.append(getSequenceFirst());
			s.append(" "); s.append("count="); s.append(getSequenceCount());

			return s.toString();
		}
	}

	public static abstract class Audio extends RaopRtpPacket {
		public Audio(final int length) {
			super(length);
		}

		protected Audio(final ChannelBuffer buffer, final int minimumSize) throws ProtocolException {
			super(buffer, minimumSize);
		}

		abstract public long getTimeStamp();
		abstract public void setTimeStamp(long timeStamp);
		abstract public long getSSrc();
		abstract public void setSSrc(long sSrc);
		abstract public ChannelBuffer getPayload();
	}

	public static final class AudioTransmit extends Audio {
		public static final byte PayloadType = 0x60;
		public static final int Length = RaopRtpPacket.Length + 4 + 4;

		public AudioTransmit(final int payloadLength) {
			super(Length + payloadLength);
			assert payloadLength >= 0;

			setPayloadType(PayloadType);
		}

		protected AudioTransmit(final ChannelBuffer buffer) throws ProtocolException {
			super(buffer, Length);
		}

		@Override
		public long getTimeStamp() {
			return getBeUInt(getBuffer(), RaopRtpPacket.Length);
		}

		@Override
		public void setTimeStamp(final long timeStamp) {
			setBeUInt(getBuffer(), RaopRtpPacket.Length, timeStamp);
		}

		@Override
		public long getSSrc() {
			return getBeUInt(getBuffer(), RaopRtpPacket.Length + 4);
		}

		@Override
		public void setSSrc(final long sSrc) {
			setBeUInt(getBuffer(), RaopRtpPacket.Length + 4, sSrc);
		}

		@Override
		public ChannelBuffer getPayload() {
			return getBuffer().slice(Length, getLength() - Length);
		}

		@Override
		public String toString() {
			final StringBuilder s = new StringBuilder();
			s.append(super.toString());

			s.append(" "); s.append("ts="); s.append(getTimeStamp());
			s.append(" "); s.append("ssrc="); s.append(getSSrc());
			s.append(" "); s.append("<"); s.append(getPayload().capacity()); s.append(" bytes payload>");

			return s.toString();
		}
	}

	public static final class AudioRetransmit extends Audio {
		public static final byte PayloadType = 0x56;
		public static final int Length = RaopRtpPacket.Length + 4 + 4 + 4;

		public AudioRetransmit(final int payloadLength) {
			super(Length + payloadLength);
			assert payloadLength >= 0;

			setPayloadType(PayloadType);
		}

		protected AudioRetransmit(final ChannelBuffer buffer) throws ProtocolException {
			super(buffer, Length);
		}

		/**
		 * First two bytes after RTP header
		 */
		public int getUnknown2Bytes() {
			return getBeUInt16(getBuffer(), RaopRtpPacket.Length);
		}

		/**
		 * First two bytes after RTP header
		 */
		public void setUnknown2Bytes(final int b) {
			setBeUInt16(getBuffer(), RaopRtpPacket.Length, b);
		}

		/**
		 * This is to be the sequence of the original
		 * packet (i.e., the sequence we requested to be
		 * retransmitted).
		 */
		public int getOriginalSequence() {
			return getBeUInt16(getBuffer(), RaopRtpPacket.Length + 2);
		}

		/**
		 * This seems is the sequence of the original
		 * packet (i.e., the sequence we requested to be
		 * retransmitted).
		 */
		public void setOriginalSequence(final int seq) {
			setBeUInt16(getBuffer(), RaopRtpPacket.Length + 2, seq);
		}

		@Override
		public long getTimeStamp() {
			return getBeUInt(getBuffer(), RaopRtpPacket.Length + 4);
		}

		@Override
		public void setTimeStamp(final long timeStamp) {
			setBeUInt(getBuffer(), RaopRtpPacket.Length + 4, timeStamp);
		}

		@Override
		public long getSSrc() {
			return getBeUInt(getBuffer(), RaopRtpPacket.Length + 4 + 4);
		}

		@Override
		public void setSSrc(final long sSrc) {
			setBeUInt(getBuffer(), RaopRtpPacket.Length + 4 + 4, sSrc);
		}

		@Override
		public ChannelBuffer getPayload() {
			return getBuffer().slice(Length, getLength() - Length);
		}

		@Override
		public String toString() {
			final StringBuilder s = new StringBuilder();
			s.append(super.toString());

			s.append(" "); s.append("?="); s.append(getUnknown2Bytes());
			s.append(" "); s.append("oseq="); s.append(getOriginalSequence());
			s.append(" "); s.append("ts="); s.append(getTimeStamp());
			s.append(" "); s.append("ssrc="); s.append(getSSrc());
			s.append(" "); s.append("<"); s.append(getPayload().capacity()); s.append(" bytes payload>");

			return s.toString();
		}
	}

	public static RaopRtpPacket decode(final ChannelBuffer buffer)
		throws ProtocolException
	{
		final RtpPacket rtpPacket = new RtpPacket(buffer, Length);

		switch (rtpPacket.getPayloadType()) {
			case TimingRequest.PayloadType: return new TimingRequest(buffer);
			case TimingResponse.PayloadType: return new TimingResponse(buffer);
			case Sync.PayloadType: return new Sync(buffer);
			case RetransmitRequest.PayloadType: return new RetransmitRequest(buffer);
			case AudioRetransmit.PayloadType: return new AudioRetransmit(buffer);
			case AudioTransmit.PayloadType: return new AudioTransmit(buffer);
			default: throw new ProtocolException("Invalid PayloadType " + rtpPacket.getPayloadType());
		}
	}

	protected RaopRtpPacket(final int length) {
		super(length);
		setVersion((byte)2);
	}

	protected RaopRtpPacket(final ChannelBuffer buffer, final int minimumSize) throws ProtocolException {
		super(buffer, minimumSize);
	}

	protected RaopRtpPacket(final ChannelBuffer buffer) throws ProtocolException {
		super(buffer);
	}
}
