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

/**
 * Base class for the various RTP packet types of RAOP/AirTunes
 */
public abstract class RaopRtpPacket extends RtpPacket {
	/**
	 * Reads an 32-bit unsigned integer from a channel buffer
	 * @param buffer the channel buffer
	 * @param index the start index
	 * @return the integer, as long to preserve the original sign
	 */
	public static long getBeUInt(final ChannelBuffer buffer, final int index) {
		return (
			((buffer.getByte(index+0) & 0xffL) << 24) |
			((buffer.getByte(index+1) & 0xffL) << 16) |
			((buffer.getByte(index+2) & 0xffL) << 8) |
			((buffer.getByte(index+3) & 0xffL) << 0)
		);
	}

	/**
	 * Writes an 32-bit unsigned integer to a channel buffer
	 * @param buffer the channel buffer
	 * @param index the start index
	 * @param value the integer, as long to preserve the original sign
	 */
	public static void setBeUInt(final ChannelBuffer buffer, final int index, final long value) {
		assert (value & ~0xffffffffL) == 0;
		buffer.setByte(index+0, (int)((value & 0xff000000L) >> 24));
		buffer.setByte(index+1, (int)((value & 0x00ff0000L) >> 16));
		buffer.setByte(index+2, (int)((value & 0x0000ff00L) >> 8));
		buffer.setByte(index+3, (int)((value & 0x000000ffL) >> 0));
	}

	/**
	 * Reads an 16-bit unsigned integer from a channel buffer
	 * @param buffer the channel buffer
	 * @param index the start index
	 * @return the short, as int to preserve the original sign
	 */
	public static int getBeUInt16(final ChannelBuffer buffer, final int index) {
		return (int)(
			((buffer.getByte(index+0) & 0xffL) << 8) |
			((buffer.getByte(index+1) & 0xffL) << 0)
		);
	}

	/**
	 * Writes an 16-bit unsigned integer to a channel buffer
	 * @param buffer the channel buffer
	 * @param index the start index
	 * @param value the short, as int to preserve the original sign
	 */
	public static void setBeUInt16(final ChannelBuffer buffer, final int index, final int value) {
		assert (value & ~0xffffL) == 0;
		buffer.setByte(index+0, (int)((value & 0xff00L) >> 8));
		buffer.setByte(index+1, (int)((value & 0x00ffL) >> 0));
	}

	/**
	 * Represents an NTP time stamp, i.e. an amount of seconds
	 * since 1900-01-01 00:00:00.000.
	 * 
	 * The value is internally represented as a 64-bit fixed
	 * point number with 32 fractional bits.
	 */
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

	/**
	 * Base class for {@link TimingRequest} and {@link TimingResponse}
	 */
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

		/**
		 * The time at which the {@link TimingRequest} was send. Copied into
		 * {@link TimingResponse} when iTunes/iOS responds to a {@link TimingRequest}
		 * @return
		 */
		public NtpTime getReferenceTime() {
			return new NtpTime(getBuffer().slice(RaopRtpPacket.Length + 4, 8));
		}

		/**
		 * The time at which a {@link TimingRequest} was received. Filled out
		 * by iTunes/iOS.
		 * @return
		 */
		public NtpTime getReceivedTime() {
			return new NtpTime(getBuffer().slice(RaopRtpPacket.Length + 12, 8));
		}

		/**
		 * The time at which a {@link TimingResponse} was sent as an response
		 * to a {@link TimingRequest}
		 * @return
		 */
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
	 * <p>
	 * Sent by the target (AirPort Express/AirReceiver) on the timing channel.
	 * Used to synchronize to the source's clock.
	 *<p>
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
	 * <p>
	 * Sent by the source (iTunes/iOS) on the timing channel.
	 * Used to synchronize to the source's clock.
	 * <p>
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

	/**
	 * Synchronization Requests. 
	 * <p>
	 * Sent by the source (iTunes/iOs) on the control channel.
	 * Used to translate RTP time stamps (frame time) into the source's time (seconds time)
	 * and from there into the target's time (provided that the two are synchronized using
	 * {@link TimingRequest} and
	 * {@link TimingResponse}.
	 *
	 */
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

		/**
		 * Gets the source's RTP time at which the sync packet was send
		 * with the latency taken into account. (i.e. the RTP time stamp
		 * of the packet supposed to be played back at {@link #getTime()}).
		 * @return the source's RTP time corresponding to {@link #getTime()} minus the latency
		 */
		public long getTimeStampMinusLatency() {
			return getBeUInt(getBuffer(), RaopRtpPacket.Length);
		}

		/**
		 * Sets the source's RTP time at which the sync packet was send
		 * with the latency taken into account. (i.e. the RTP time stamp
		 * of the packet supposed to be played back at {@link #getTime()}).
		 * @return the source's RTP time corresponding to {@link #getTime()} minus the latency
		 */
		public void setTimeStampMinusLatency(final long value) {
			setBeUInt(getBuffer(), RaopRtpPacket.Length, value);
		}

		/**
		 * The source's NTP time at which the sync packet was send
		 * @return the source's NTP time corresponding to the RTP time returned by {@link #getTimeStamp()}
		 */
		public NtpTime getTime() {
			return new NtpTime(getBuffer().slice(RaopRtpPacket.Length + 4, 8));
		}

		/**
		 * Gets the current RTP time (frame time) at which the sync packet was sent,
		 * <b>disregarding</b> the latency. (i.e. approximately the RTP time stamp
		 * of the packet sent at {@link #getTime()})
		 * @return the source's RTP time corresponding to {@link #getTime()}
		 */
		public long getTimeStamp() {
			return getBeUInt(getBuffer(), RaopRtpPacket.Length + 4 + 8);
		}

		/**
		 * Sets the current RTP time (frame time) at which the sync packet was sent,
		 * <b>disregarding</b> the latency. (i.e. approximately the RTP time stamp
		 * of the packet sent at {@link #getTime()})
		 * @param value the source's RTP time corresponding to {@link #getTime()}
		 */
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

	/**
	 * Retransmit request.
	 * <p>
	 * Sent by the target (Airport Express/AirReceiver) on the control channel. Used to let the
	 * source know about missing packets.
	 * <p>
	 * The source is supposed to respond by re-sending the packets with sequence numbers
	 * {@link #getSequenceFirst()} to {@link #getSequenceFirst()} + {@link #getSequenceCount()}.
	 * <p>
	 * The retransmit responses are sent on the <b>control</b> channel, and use packet format
	 * {@link AudioRetransmit} instead of {@link AudioTransmit}.
	 * 
	 */
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

		/**
		 * Gets the sequence number of the first missing packet
		 * @return sequence number
		 */
		public int getSequenceFirst() {
			return getBeUInt16(getBuffer(), RaopRtpPacket.Length);
		}

		/**
		 * Sets the sequence number of the first missing packet
		 * @param value sequence number
		 */
		public void setSequenceFirst(final int value) {
			setBeUInt16(getBuffer(), RaopRtpPacket.Length, value);
		}

		/**
		 * Gets the number of missing packets
		 * @return number of missing packets
		 */
		public int getSequenceCount() {
			return getBeUInt16(getBuffer(), RaopRtpPacket.Length + 2);
		}

		/**
		 * Sets the number of missing packets
		 * @param value number of missing packets
		 */
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

	/**
	 * Base class for {@link AudioTransmit} and {@link AudioRetransmit}.
	 */
	public static abstract class Audio extends RaopRtpPacket {
		public Audio(final int length) {
			super(length);
		}

		protected Audio(final ChannelBuffer buffer, final int minimumSize) throws ProtocolException {
			super(buffer, minimumSize);
		}

		/**
		 * Gets the packet's RTP time stamp (frame time)
		 * @return RTP timestamp in frames
		 */
		abstract public long getTimeStamp();
		
		/**
		 * Gets the packet's RTP time stamp (frame time)
		 * @param timeStamp RTP timestamp in frames
		 */
		abstract public void setTimeStamp(long timeStamp);
		
		/**
		 * Unknown, seems to be always zero
		 */
		abstract public long getSSrc();

		/**
		 * Unknown, seems to be always zero
		 */
		abstract public void setSSrc(long sSrc);
		
		/**
		 * ChannelBuffer containing the audio data
		 * @return channel buffer containing audio data
		 */
		abstract public ChannelBuffer getPayload();
	}

	/**
	 * Audio data transmission.
	 * <p>
	 * Sent by the source (iTunes/iOS) on the audio channel.
	 */
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

	/**
	 * Audio data re-transmission.
	 * <p>
	 * Sent by the source (iTunes/iOS) on the control channel in response
	 * to {@link RetransmitRequest}.
	 */
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

	/**
	 * Creates an RTP packet from a {@link ChannelBuffer}, using the
	 * sub-class of {@link RaopRtpPacket} indicated by the packet's
	 * {@link #getPayloadType()}
	 * 
	 * @param buffer ChannelBuffer containing the packet
	 * @return Instance of one of the sub-classes of {@link RaopRtpPacket}
	 * @throws ProtocolException if the packet is invalid.
	 */
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
