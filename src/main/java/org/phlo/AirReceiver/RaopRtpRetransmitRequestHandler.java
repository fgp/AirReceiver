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

import java.util.*;
import java.util.logging.Logger;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

public class RaopRtpRetransmitRequestHandler extends SimpleChannelUpstreamHandler {
	private static Logger s_logger = Logger.getLogger(RaopRtpRetransmitRequestHandler.class.getName());

	private static final double RetransmitInFlightLimit = 128;
	private static final int RetransmitAttempts = 2;

	private class MissingPacket {
		public final int sequence;
		public final long requiredUntilFrameTime;
		public final double requiredUntilSecondsTime;
		public int retransmitRequestCount = 0;
		public double expectedUntilSecondsTime;

		public MissingPacket(final int _sequence, final double nextSecondsTimee) {
			sequence = _sequence;
			requiredUntilFrameTime = convertSequenceToFrameTime(_sequence);
			requiredUntilSecondsTime = m_audioClock.convertFrameToSecondsTime(requiredUntilFrameTime);
			computeExpectedUntil(nextSecondsTimee);
		}

		public void sentRetransmitRequest(final double nextSecondsTimee) {
			++retransmitRequestCount;
			computeExpectedUntil(nextSecondsTimee);
		}

		private void computeExpectedUntil(final double nextSecondsTimee) {
			expectedUntilSecondsTime = 0.5 * nextSecondsTimee + 0.5 * m_audioClock.convertFrameToSecondsTime(requiredUntilFrameTime);
		}
	}

	private final AudioClock m_audioClock;
	private final long m_framesPerPacket;

	private int m_latestReceivedSequence = -1;
	private long m_latestReceivedSequenceFrameTime;
	private static final List<MissingPacket> m_missingPackets = new java.util.LinkedList<MissingPacket>();

	private int m_retransmitRequestSequence = 0;

	public RaopRtpRetransmitRequestHandler(final AudioStreamInformationProvider streamInfoProvider, final AudioClock audioClock) {
		m_framesPerPacket = streamInfoProvider.getFramesPerPacket();
		m_audioClock = audioClock;
	}

	private void markRetransmitted(final int sequence, final double nextSecondsTimee) {
		final Iterator<MissingPacket> i = m_missingPackets.iterator();
		while (i.hasNext()) {
			final MissingPacket missingPacket = i.next();
			if (missingPacket.sequence == sequence) {
				s_logger.fine("Packet " + sequence + " arrived " + (missingPacket.expectedUntilSecondsTime - nextSecondsTimee) + " seconds before it was due");
				i.remove();
			}
		}
	}

	private void markMissing(final int sequence, final double nextSecondsTime) {
		final MissingPacket missingPacket = new MissingPacket(sequence, nextSecondsTime);
		if (missingPacket.requiredUntilSecondsTime > nextSecondsTime) {
			s_logger.fine("Packet " + sequence + " expected to arive in " + (missingPacket.expectedUntilSecondsTime - nextSecondsTime) + " seconds");

			m_missingPackets.add(missingPacket);
		}
		else {
			s_logger.warning("Packet " + sequence + " was required " + (nextSecondsTime - missingPacket.expectedUntilSecondsTime ) + " seconds ago, not requesting retransmit");
		}

		while (m_missingPackets.size() > RetransmitInFlightLimit) {
			final MissingPacket m = m_missingPackets.get(0);
			m_missingPackets.remove(0);

			s_logger.warning("Packet " + sequence + " overflowed in-flight retransmit count, giving up on old packet " + m.sequence);
		}
	}

	private synchronized void requestRetransmits(final Channel channel, final double nextSecondsTimee) {

		RaopRtpPacket.RetransmitRequest retransmitRequest = null;

		final Iterator<MissingPacket> missingPacketIterator = m_missingPackets.iterator();
		while (missingPacketIterator.hasNext()) {
			final MissingPacket missingPacket = missingPacketIterator.next();

			if (missingPacket.requiredUntilSecondsTime <= nextSecondsTimee) {
				s_logger.warning("Packet " + missingPacket.sequence + " was required " + (nextSecondsTimee - missingPacket.requiredUntilSecondsTime) + " secons ago, giving up");

				missingPacketIterator.remove();
				continue;
			}

			if (missingPacket.expectedUntilSecondsTime > nextSecondsTimee)
				continue;

			if (missingPacket.retransmitRequestCount >= RetransmitAttempts) {
				s_logger.warning("Packet " + missingPacket.sequence + " overdue " + (nextSecondsTimee - missingPacket.expectedUntilSecondsTime) + " seconds after " + missingPacket.retransmitRequestCount + " retransmit requests, giving up");

				missingPacketIterator.remove();
				continue;
			}
			else {
				final int retransmitRequestCountPrevious = missingPacket.retransmitRequestCount;
				final double expectedUntilSecondsTimePrevious = missingPacket.expectedUntilSecondsTime;
				missingPacket.sentRetransmitRequest(nextSecondsTimee);

				s_logger.fine("Packet " + missingPacket.sequence + " overdue " + (nextSecondsTimee - expectedUntilSecondsTimePrevious) + " seconds after " + retransmitRequestCountPrevious + " retransmit requests, requesting again expecting response in " + (missingPacket.expectedUntilSecondsTime - nextSecondsTimee) + " seconds");
			}

			if (
				(retransmitRequest != null) &&
				(sequenceAdd(retransmitRequest.getSequenceFirst(), retransmitRequest.getSequenceCount()) != missingPacket.sequence)
			) {
				if (channel.isOpen() && channel.isWritable())
					channel.write(retransmitRequest);
				retransmitRequest = null;
			}

			if (retransmitRequest == null) {
				m_retransmitRequestSequence = sequenceSuccessor(m_retransmitRequestSequence);
				retransmitRequest = new RaopRtpPacket.RetransmitRequest();
				retransmitRequest.setSequence(m_retransmitRequestSequence);
				retransmitRequest.setSequenceFirst(missingPacket.sequence);
				retransmitRequest.setSequenceCount(1);
			}
			else {
				retransmitRequest.setSequenceCount(retransmitRequest.getSequenceCount() + 1);
			}
		}
		if (retransmitRequest != null) {
			if (channel.isOpen() && channel.isWritable())
				channel.write(retransmitRequest);
		}
	}

	@Override
	public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent evt)
		throws Exception
	{
		if (evt.getMessage() instanceof RaopRtpPacket.AudioTransmit)
			audioTransmitReceived(ctx, (RaopRtpPacket.AudioTransmit)evt.getMessage());
		else if (evt.getMessage() instanceof RaopRtpPacket.AudioRetransmit)
			audioRetransmitReceived(ctx, (RaopRtpPacket.AudioRetransmit)evt.getMessage());

		super.messageReceived(ctx, evt);
	}

	private synchronized void audioRetransmitReceived(final ChannelHandlerContext ctx, final RaopRtpPacket.AudioRetransmit audioPacket) {
		final double nextSecondsTimee = m_audioClock.getNextSecondsTime();

		markRetransmitted(audioPacket.getOriginalSequence(), nextSecondsTimee);
		requestRetransmits(ctx.getChannel(), nextSecondsTimee);
	}

	private synchronized void audioTransmitReceived(final ChannelHandlerContext ctx, final RaopRtpPacket.AudioTransmit audioPacket) {
		final double nextSecondsTimee = m_audioClock.getNextSecondsTime();

		markRetransmitted(audioPacket.getSequence(), nextSecondsTimee);

		final long delta;
		if (m_latestReceivedSequence < 0)
			delta = 1;
		else
			delta = sequenceDelta(m_latestReceivedSequence, audioPacket.getSequence());

		final int expectedSequence = sequenceSuccessor(m_latestReceivedSequence);
		if (delta > 0) {
			m_latestReceivedSequence = audioPacket.getSequence();
			m_latestReceivedSequenceFrameTime = audioPacket.getTimeStamp();
		}

		if (delta == 1) {
			/* No reordered or missing packets */
		}
		else if ((delta > 1) && (delta <= RetransmitInFlightLimit)) {
			s_logger.fine("Packet sequence number increased by " + delta + ", " + (delta-1) + " packet(s) missing,");

			for(int s = expectedSequence; s != audioPacket.getSequence(); s = sequenceSuccessor(s))
				markMissing(s, nextSecondsTimee);
		}
		else if (delta < 0) {
			s_logger.fine("Packet sequence number decreased by " + (-delta) + ", assuming delayed packet");
		}
		else {
			s_logger.warning("Packet sequence number jumped to " + audioPacket.getSequence() + ", assuming sequences number are out of sync");

			m_missingPackets.clear();
		}

		requestRetransmits(ctx.getChannel(), nextSecondsTimee);
	}

	private long convertSequenceToFrameTime(final int sequence) {
		return m_latestReceivedSequenceFrameTime + sequenceDelta(m_latestReceivedSequence, sequence) * m_framesPerPacket;
	}

	private static long sequenceDistance(final int from, final int to) {
		assert (from & 0xffff) == from;
		assert (to & 0xffff) == to;

		return (0x10000 + to - from) % 0x10000;
	}

	private static long sequenceDelta(final int a, final int b) {
		final long d = sequenceDistance(a, b);
		if (d < 0x8000)
			return d;
		else
			return (d - 0x10000);
	}

	private static int sequenceAdd(final int seq, final long delta) {
		return (seq + (int)(delta % 0x10000L)) % 0x10000;
	}

	private static int sequenceSuccessor(final int seq) {
		return (seq + 1) % 0x10000;
	}

	@SuppressWarnings("unused")
	private static int sequencePredecessor(final int seq) {
		return (0x10000 + seq - 1) % 0x10000;
	}
}
