package org.phlo.AirReceiver;

import java.util.*;
import java.util.logging.Logger;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

public class RaopRtpRetransmitRequestHandler extends SimpleChannelUpstreamHandler {
	private static Logger s_logger = Logger.getLogger(RaopRtpRetransmitRequestHandler.class.getName());

	private static final double RetransmitRequestsLimitSeconds = 1.0;
	private static final double DuplicateDetectThresholdSeconds = 1.0;
	private static final double RetransmitTimeoutSeconds = 0.3;
	private static final int RetransmitAttempts = 3;
	
	private static class MissingPacket {
		public int sequence;
		public int retransmitRequestCount = 0;
		public long retransmitRequestedNanoTime = Long.MIN_VALUE;
	}
	
	private final int m_retransmitRequestsLimitPackets;
	private final int m_duplicateDetectThresholdPackets;
	private int m_lastSequence = -1;
	private static final List<MissingPacket> m_missingPackets = new java.util.LinkedList<MissingPacket>();
	
	public RaopRtpRetransmitRequestHandler(AudioStreamInformationProvider streamInfoProvider) {
		final double packetsPerSecond = streamInfoProvider.getPacketsPerSecond();
		m_retransmitRequestsLimitPackets = (int)Math.ceil(RetransmitRequestsLimitSeconds * packetsPerSecond);
		m_duplicateDetectThresholdPackets = (int)Math.ceil(DuplicateDetectThresholdSeconds * packetsPerSecond);
		
		s_logger.info("Expecting " + packetsPerSecond + " packets per second, maximum number of in-flight retransmits is " + m_retransmitRequestsLimitPackets + ", duplicate detection window is " + m_duplicateDetectThresholdPackets + " packets");
	}
	
	private void markRetransmitted(int sequence) {
		Iterator<MissingPacket> i = m_missingPackets.iterator();
		while (i.hasNext()) {
			MissingPacket missingPacket = i.next();
			if (missingPacket.sequence == sequence)
				i.remove();
		}
	}
	
	private void markMissing(int sequence) {
		MissingPacket missingPacket = new MissingPacket();
		missingPacket.sequence = sequence;
		m_missingPackets.add(missingPacket);
		
		while (m_missingPackets.size() > m_retransmitRequestsLimitPackets)
			m_missingPackets.remove(0);
	}
	
	private void requestRetransmits(ChannelHandlerContext ctx) {
		final long nowNanoTime = System.nanoTime();
		
		RaopRtpPacket.RetransmitRequest retransmitRequest = null;
		
		Iterator<MissingPacket> missingPacketIterator = m_missingPackets.iterator();
		while (missingPacketIterator.hasNext()) {
			MissingPacket missingPacket = missingPacketIterator.next();

			if (missingPacket.retransmitRequestedNanoTime > nowNanoTime - RetransmitTimeoutSeconds*1e9)
				continue;
			
			missingPacket.retransmitRequestedNanoTime = nowNanoTime;
			++missingPacket.retransmitRequestCount;
			if (missingPacket.retransmitRequestCount > 1)
				s_logger.fine("Packet retransmit timed out, re-requesting retransmit of " + missingPacket.sequence);
			if (missingPacket.retransmitRequestCount >= RetransmitAttempts) {
				s_logger.fine("Packet retransmit request limit reached, last request for retransmit of " + missingPacket.sequence);
				missingPacketIterator.remove();
			}

			if (
				(retransmitRequest != null) &&
				((retransmitRequest.getSequence() + retransmitRequest.getSequenceCount()) % 0x10000 != missingPacket.sequence)
			) {
				if (ctx.getChannel().isOpen())
					ctx.getChannel().write(retransmitRequest);
				retransmitRequest = null;
			}
			
			if (retransmitRequest == null) {
				retransmitRequest = new RaopRtpPacket.RetransmitRequest();
				retransmitRequest.setSequenceFirst(missingPacket.sequence);
				retransmitRequest.setSequenceCount(1);
			}
			else {
				retransmitRequest.setSequenceCount(retransmitRequest.getSequenceCount() + 1);
			}
		}
		if (retransmitRequest != null) {
			if (ctx.getChannel().isOpen())
				ctx.getChannel().write(retransmitRequest);
		}
	}
			
	@Override
	public synchronized void messageReceived(ChannelHandlerContext ctx, MessageEvent evt)
		throws Exception
	{
		RaopRtpPacket packet = (RaopRtpPacket)evt.getMessage();

		if (packet instanceof RaopRtpPacket.AudioTransmit)
			audioTransmitReceived(ctx, (RaopRtpPacket.AudioTransmit)packet);
		else if (packet instanceof RaopRtpPacket.AudioRetransmit)
			audioRetransmitReceived(ctx, (RaopRtpPacket.AudioRetransmit)packet);

		super.messageReceived(ctx, evt);
	}

	private void audioRetransmitReceived(ChannelHandlerContext ctx, RaopRtpPacket.AudioRetransmit audioPacket) {
		markRetransmitted(audioPacket.getOriginalSequence());
	}
	
	private void audioTransmitReceived(ChannelHandlerContext ctx, RaopRtpPacket.AudioTransmit audioPacket) {
		markRetransmitted(audioPacket.getSequence());

		if (m_lastSequence < 0)
			m_lastSequence = (0x10000 + audioPacket.getSequence() - 1) % 0x10000;;
		
		int increase = (0x10000 + audioPacket.getSequence() - m_lastSequence) % 0x10000;
		int decrease = (0x10000 + m_lastSequence - audioPacket.getSequence()) % 0x10000;
		if (increase == 1) {
			m_lastSequence = audioPacket.getSequence();
		}
		else if ((increase > 1) && (increase <= m_retransmitRequestsLimitPackets)) {
			s_logger.fine("Packet sequence number increased by " + increase + ", assuming " + (increase-1) + " packet(s) got lost,");
			for(int s = (m_lastSequence + 1) % 0x10000; s != audioPacket.getSequence(); s = (s + 1) % 0x10000)
				markMissing(s);
			m_lastSequence = audioPacket.getSequence();;
		}
		else if (decrease <= m_duplicateDetectThresholdPackets) {
			s_logger.fine("Packet sequence number decreased by " + decrease + ", assuming duplicate or late packet");
		}
		else {
			s_logger.fine("Packet sequence number jumped to " + audioPacket.getSequence() + ", assuming sequences got out of sync, adjusting expected sequence");
			m_lastSequence = audioPacket.getSequence();;
		}

		requestRetransmits(ctx);
	}
}