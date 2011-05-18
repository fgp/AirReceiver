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

/**
 * Handles packet retransmissions.
 * <p>
 * Sends {@link RaopRtpPacket.RetransmitRequest}Êpacket in response to missing packets,
 * and resends those requests after a timeout period until the packet arrives.
 * <p>
 * Uses an {@link AudioClock} as it's time source, any thus only re-requests packets
 * which can reasonably be expected to arrive before their play back time.
 *
 */
public class RaopRtpRetransmitRequestHandler extends SimpleChannelUpstreamHandler {
	private static Logger s_logger = Logger.getLogger(RaopRtpRetransmitRequestHandler.class.getName());

	/**
	 * Maximal number of in-flight (i.e. not yet fulfilled) retransmit requests
	 */
	private static final double RetransmitInFlightLimit = 128;
	
	/**
	 * Maximum number of retransmit requests sent per packet
	 */
	private static final int RetransmitAttempts = 2;

	/**
	 * Represents a missing packet
	 */
	private class MissingPacket {
		/**
		 *  Packet's sequence number
		 */
		public final int sequence;
		
		/**
		 * Packet must be placed on the audio output queue no later than this frame time
		 */
		public final long requiredUntilFrameTime;
		
		/**
		 * Packet must be placed on the audio output queue no later than this seconds time
		 */
		public final double requiredUntilSecondsTime;
		
		/**
		 * Number of retransmit requests already sent for the packet
		 */
		public int retransmitRequestCount = 0;
		
		/**
		 * Packet expected to arrive until this seconds time. If not, a retransmit request
		 * is sent.
		 */
		public double expectedUntilSecondsTime;

		/**
		 * Creates a MissingPacket instance for a given sequence, using the provided
		 * time to compute the times at which the packet is expected.
		 * 
		 * @param _sequence sequence number
		 * @param nextSecondsTime next possible play back time
		 */
		public MissingPacket(final int _sequence, final double nextSecondsTime) {
			sequence = _sequence;
			requiredUntilFrameTime = convertSequenceToFrameTime(_sequence);
			requiredUntilSecondsTime = m_audioClock.convertFrameToSecondsTime(requiredUntilFrameTime);
			computeExpectedUntil(nextSecondsTime);
		}

		/**
		 * Updates the state after a retransmit request has been sent.
		 * @param nextSecondsTime next possible play back time
		 */
		public void sentRetransmitRequest(final double nextSecondsTime) {
			++retransmitRequestCount;
			computeExpectedUntil(nextSecondsTime);
		}

		/**
		 * Updates the time until which we expect the packet to arrive.
		 * @param nextSecondsTime next possible play back time
		 */
		private void computeExpectedUntil(final double nextSecondsTimee) {
			expectedUntilSecondsTime = 0.5 * nextSecondsTimee + 0.5 * m_audioClock.convertFrameToSecondsTime(requiredUntilFrameTime);
		}
	}

	/**
	 * Time source
	 */
	private final AudioClock m_audioClock;
	
	/**
	 * Frames per packet. Used to interpolate the
	 * RTP time stamps of missing packets.
	 */
	private final long m_framesPerPacket;

	/**
	 * Latest sequence number received so far
	 */
	private int m_latestReceivedSequence = -1;
	
	/**
	 * RTP frame time corresponding to packet
	 * with the latest sequence number.
	 */
	private long m_latestReceivedSequenceFrameTime;
	
	/**
	 * List of in-flight retransmit requests
	 */
	private static final List<MissingPacket> m_missingPackets = new java.util.LinkedList<MissingPacket>();

	/**
	 * Header sequence number for retransmit requests
	 */
	private int m_retransmitRequestSequence = 0;

	public RaopRtpRetransmitRequestHandler(final AudioStreamInformationProvider streamInfoProvider, final AudioClock audioClock) {
		m_framesPerPacket = streamInfoProvider.getFramesPerPacket();
		m_audioClock = audioClock;
	}

	/**
	 * Mark the packet as retransmitted, i.e. remove it from the list of
	 * in-flight retransmit requests.
	 * 
	 * @param sequence sequence number of packet
	 * @param nextSecondsTime next possible play back time
	 */
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

	/**
	 * Mark the packet is missing, i.e. add an entry to the list of
	 * in-flight retransmit requests.
	 * 
	 * @param sequence sequence number of packet
	 * @param nextSecondsTime next possible play back time
	 */
	private void markMissing(final int sequence, final double nextSecondsTime) {
		/* Add packet to list of in-flight retransmit requests */
		final MissingPacket missingPacket = new MissingPacket(sequence, nextSecondsTime);
		if (missingPacket.requiredUntilSecondsTime > nextSecondsTime) {
			s_logger.fine("Packet " + sequence + " expected to arive in " + (missingPacket.expectedUntilSecondsTime - nextSecondsTime) + " seconds");

			m_missingPackets.add(missingPacket);
		}
		else {
			s_logger.warning("Packet " + sequence + " was required " + (nextSecondsTime - missingPacket.expectedUntilSecondsTime ) + " seconds ago, not requesting retransmit");
		}

		/* Forget about old missing packets if we exceeded the number
		 * of in-flight retransmit requests
		 */
		while (m_missingPackets.size() > RetransmitInFlightLimit) {
			final MissingPacket m = m_missingPackets.get(0);
			m_missingPackets.remove(0);

			s_logger.warning("Packet " + sequence + " overflowed in-flight retransmit count, giving up on old packet " + m.sequence);
		}
	}

	/**
	 * Scan the list of in-flight retransmit requests and send
	 * {@link RetransmitRequest} packets if it's past the time
	 * at which we expected the packet to arrive
	 * 
	 * @param channel channel used to send retransmit requests
	 * @param nextSecondsTime
	 */
	private synchronized void requestRetransmits(final Channel channel, final double nextSecondsTime) {
		/* The retransmit request we're currently building */
		RaopRtpPacket.RetransmitRequest retransmitRequest = null;

		/* Run through open retransmit requests */
		final Iterator<MissingPacket> missingPacketIterator = m_missingPackets.iterator();
		while (missingPacketIterator.hasNext()) {
			final MissingPacket missingPacket = missingPacketIterator.next();

			/* If it's past the time at which the packet would have needed to be queued,
			 * warn and forget about it
			 */
			if (missingPacket.requiredUntilSecondsTime <= nextSecondsTime) {
				s_logger.warning("Packet " + missingPacket.sequence + " was required " + (nextSecondsTime - missingPacket.requiredUntilSecondsTime) + " secons ago, giving up");

				missingPacketIterator.remove();
				continue;
			}

			/* If the packet isn't expected until later,
			 * skip it for now */
			if (missingPacket.expectedUntilSecondsTime > nextSecondsTime)
				continue;

			/* Ok, the packet is overdue */
			
			if (missingPacket.retransmitRequestCount >= RetransmitAttempts) {
				/* If the packet was already requests too often,
				 * warn and forget about it */
				s_logger.warning("Packet " + missingPacket.sequence + " overdue " + (nextSecondsTime - missingPacket.expectedUntilSecondsTime) + " seconds after " + missingPacket.retransmitRequestCount + " retransmit requests, giving up");

				missingPacketIterator.remove();
				continue;
			}
			else {
				/* Log that we're about to request retransmission */
				final int retransmitRequestCountPrevious = missingPacket.retransmitRequestCount;
				final double expectedUntilSecondsTimePrevious = missingPacket.expectedUntilSecondsTime;
				missingPacket.sentRetransmitRequest(nextSecondsTime);

				s_logger.fine("Packet " + missingPacket.sequence + " overdue " + (nextSecondsTime - expectedUntilSecondsTimePrevious) + " seconds after " + retransmitRequestCountPrevious + " retransmit requests, requesting again expecting response in " + (missingPacket.expectedUntilSecondsTime - nextSecondsTime) + " seconds");
			}

			/* Ok, really request re-transmission */
			
			if (
				(retransmitRequest != null) &&
				(sequenceAdd(retransmitRequest.getSequenceFirst(), retransmitRequest.getSequenceCount()) != missingPacket.sequence)
			) {
				/* There is a current retransmit request, but the sequence cannot be appended.
				 * We transmit the current request and start building a new one
				 */
				if (channel.isOpen() && channel.isWritable())
					channel.write(retransmitRequest);
				retransmitRequest = null;
			}
			
			/* If there still is a current retransmit request, the sequence can be appended */

			if (retransmitRequest == null) {
				/* Create new retransmit request */
				m_retransmitRequestSequence = sequenceSuccessor(m_retransmitRequestSequence);
				retransmitRequest = new RaopRtpPacket.RetransmitRequest();
				retransmitRequest.setSequence(m_retransmitRequestSequence);
				retransmitRequest.setSequenceFirst(missingPacket.sequence);
				retransmitRequest.setSequenceCount(1);
			}
			else {
				/* Append sequnce to current retransmit request */
				retransmitRequest.setSequenceCount(retransmitRequest.getSequenceCount() + 1);
			}
		}
		if (retransmitRequest != null) {
			/* Send the retransmit request we were building when the loop ended */
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

		/* Request retransmits if necessary */
		requestRetransmits(ctx.getChannel(), m_audioClock.getNextSecondsTime());
	}

	private synchronized void audioRetransmitReceived(final ChannelHandlerContext ctx, final RaopRtpPacket.AudioRetransmit audioPacket) {
		final double nextSecondsTime = m_audioClock.getNextSecondsTime();

		/* Mark packet as retransmitted */
		markRetransmitted(audioPacket.getOriginalSequence(), nextSecondsTime);
	}

	private synchronized void audioTransmitReceived(final ChannelHandlerContext ctx, final RaopRtpPacket.AudioTransmit audioPacket) {
		final double nextSecondsTime = m_audioClock.getNextSecondsTime();

		/* Mark packet as retransmitted.
		 * Doing this here prevents sending out further retransmit requests for packets
		 * which simply were delayed
		 */
		markRetransmitted(audioPacket.getSequence(), nextSecondsTime);

		/* Compute delta between the last and the current Sequence */
		final long delta;
		if (m_latestReceivedSequence < 0)
			delta = 1;
		else
			delta = sequenceDelta(m_latestReceivedSequence, audioPacket.getSequence());

		/* Remember the sequence we expected, then update the latest received sequence
		 * and it's frame time iff the new sequence is larger than the old one
		 */
		final int expectedSequence = sequenceSuccessor(m_latestReceivedSequence);
		if (delta > 0) {
			m_latestReceivedSequence = audioPacket.getSequence();
			m_latestReceivedSequenceFrameTime = audioPacket.getTimeStamp();
		}

		if (delta == 1) {
			/* No reordered or missing packets */
		}
		else if ((delta > 1) && (delta <= RetransmitInFlightLimit)) {
			/* Previous packet reordered/delayed or missing */
			s_logger.fine("Packet sequence number increased by " + delta + ", " + (delta-1) + " packet(s) missing,");

			for(int s = expectedSequence; s != audioPacket.getSequence(); s = sequenceSuccessor(s))
				markMissing(s, nextSecondsTime);
		}
		else if (delta < 0) {
			/* Delayed packet */
			s_logger.fine("Packet sequence number decreased by " + (-delta) + ", assuming delayed packet");
		}
		else {
			/* Unsynchronized sequences */
			s_logger.warning("Packet sequence number jumped to " + audioPacket.getSequence() + ", assuming sequences number are out of sync");

			m_missingPackets.clear();
		}
	}

	/**
	 * Interpolate RTP frame time of missing packet
	 * @param sequence sequence of missing packet
	 * @return interpolated frame time of missing packet
	 */
	private long convertSequenceToFrameTime(final int sequence) {
		return m_latestReceivedSequenceFrameTime + sequenceDelta(m_latestReceivedSequence, sequence) * m_framesPerPacket;
	}

	/**
	 * Returns the number of sequences between from and to, including
	 * to.
	 * 
	 * @param from first sequence
	 * @param to seconds sequence
	 * @return number of intermediate sequences
	 */
	private static long sequenceDistance(final int from, final int to) {
		assert (from & 0xffff) == from;
		assert (to & 0xffff) == to;

		return (0x10000 + to - from) % 0x10000;
	}

	/**
	 * Returns the difference between to sequences. Since sequences
	 * are circular, they're not totally ordered, and hence it's
	 * ambiguous whether the delta is positive or negative. We return
	 * the number with the smaller <b>absolute</b> value.
	 * 
	 * @param a first sequence
	 * @param b second sequence
	 * @return the delta between a and b
	 */
	private static long sequenceDelta(final int a, final int b) {
		final long d = sequenceDistance(a, b);
		if (d < 0x8000)
			return d;
		else
			return (d - 0x10000);
	}

	/**
	 * Adds a delta to a given sequence and returns the resulting
	 * sequence.
	 * 
	 * @param seq sequence
	 * @param delta delta to add
	 * @return sequence incremented or decremented by delta
	 */
	private static int sequenceAdd(final int seq, final long delta) {
		return (0x10000 + seq + (int)(delta % 0x10000L)) % 0x10000;
	}

	/**
	 * Returns the immediate successor sequence of the given sequence
	 * @param seq sequence
	 * @return successor of sequence
	 */
	private static int sequenceSuccessor(final int seq) {
		return sequenceAdd(seq, 1);
	}

	/**
	 * Returns the immediate predecessor sequence of the given sequence
	 * @param seq sequence
	 * @return predecessor of sequence
	 */
	@SuppressWarnings("unused")
	private static int sequencePredecessor(final int seq) {
		return sequenceAdd(seq, -1);
	}
}
