package org.phlo.AirReceiver;

import java.util.*;
import java.util.logging.Logger;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

public class RaopRtpRetransmitRequestHandler extends SimpleChannelUpstreamHandler {
	private static Logger s_logger = Logger.getLogger(RaopRtpRetransmitRequestHandler.class.getName());

	private static final double RetransmitRequestsAgeLimitSeconds = 0.8;
	private static final double RetransmitRequestsOpenLimit = 32;
	private static final double DuplicateDetectThresholdSeconds = 5;
	private static final double RetransmitTimeoutSeconds = 0.2;
	private static final int RetransmitAttempts = 3;
	
	private static class MissingPacket {
		public int sequence;
		public int retransmitRequestCount = 0;
		public long retransmitRequestedNanoTime = Long.MIN_VALUE;
	}
	
	private final int m_retransmitRequestsAgeLimitPackets;
	private final int m_duplicateDetectThresholdPackets;
	private int m_lastSequence = -1;
	private static final List<MissingPacket> m_missingPackets = new java.util.LinkedList<MissingPacket>();
	private int m_retransmitRequestSequence = 0;
	
	public RaopRtpRetransmitRequestHandler(AudioStreamInformationProvider streamInfoProvider) {
		final double packetsPerSecond = streamInfoProvider.getPacketsPerSecond();
		m_retransmitRequestsAgeLimitPackets = (int)Math.ceil(RetransmitRequestsAgeLimitSeconds * packetsPerSecond);
		m_duplicateDetectThresholdPackets = (int)Math.ceil(DuplicateDetectThresholdSeconds * packetsPerSecond);
		
		s_logger.info("Expecting " + packetsPerSecond + " packets per second, maximum number of in-flight retransmits is " + RetransmitRequestsOpenLimit + ", maximum age of packet for retransmit request is " + m_retransmitRequestsAgeLimitPackets + " packets, size of duplicate detection window is " + m_duplicateDetectThresholdPackets + " packets");
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
		
		while (m_missingPackets.size() > RetransmitRequestsOpenLimit) {
			MissingPacket m = m_missingPackets.get(0);
			s_logger.warning("Packet " + m.sequence + " wasn't retransmitted in time");
			m_missingPackets.remove(0);
		}
	}
	
	private void requestRetransmits(ChannelHandlerContext ctx) {
		final long nowNanoTime = System.nanoTime();
		
		RaopRtpPacket.RetransmitRequest retransmitRequest = null;
		
		Iterator<MissingPacket> missingPacketIterator = m_missingPackets.iterator();
		while (missingPacketIterator.hasNext()) {
			MissingPacket missingPacket = missingPacketIterator.next();
			
			int missingPacketAge = (0x1000 + m_lastSequence - missingPacket.sequence) % 0x1000;

			if (missingPacketAge > m_retransmitRequestsAgeLimitPackets)
				continue;
			if (missingPacket.retransmitRequestedNanoTime > nowNanoTime - RetransmitTimeoutSeconds*1e9)
				continue;
			if (missingPacket.retransmitRequestCount >= RetransmitAttempts)
				continue;
			
			missingPacket.retransmitRequestedNanoTime = nowNanoTime;
			++missingPacket.retransmitRequestCount;
			s_logger.fine("Packet " + missingPacket.sequence + " still missing after " + missingPacket.retransmitRequestCount + " retransmit requests, sending another one");

			if (
				(retransmitRequest != null) &&
				(((retransmitRequest.getSequence() + retransmitRequest.getSequenceCount()) % 0x10000) != missingPacket.sequence)
			) {
				if (ctx.getChannel().isOpen())
					ctx.getChannel().write(retransmitRequest);
				retransmitRequest = null;
			}
			
			if (retransmitRequest == null) {
				m_retransmitRequestSequence = (m_retransmitRequestSequence + 1) % 0x10000;
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
			if (ctx.getChannel().isOpen())
				ctx.getChannel().write(retransmitRequest);
		}
	}
			
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent evt)
		throws Exception
	{
		RaopRtpPacket packet = (RaopRtpPacket)evt.getMessage();

		if (packet instanceof RaopRtpPacket.AudioTransmit)
			audioTransmitReceived(ctx, (RaopRtpPacket.AudioTransmit)packet);
		else if (packet instanceof RaopRtpPacket.AudioRetransmit)
			audioRetransmitReceived(ctx, (RaopRtpPacket.AudioRetransmit)packet);

		super.messageReceived(ctx, evt);
	}

	private synchronized void audioRetransmitReceived(ChannelHandlerContext ctx, RaopRtpPacket.AudioRetransmit audioPacket) {
		markRetransmitted(audioPacket.getOriginalSequence());
	}
	
	private synchronized void audioTransmitReceived(ChannelHandlerContext ctx, RaopRtpPacket.AudioTransmit audioPacket) {
		markRetransmitted(audioPacket.getSequence());

		if (m_lastSequence < 0)
			m_lastSequence = (0x10000 + audioPacket.getSequence() - 1) % 0x10000;;
		
		int increase = (0x10000 + audioPacket.getSequence() - m_lastSequence) % 0x10000;
		int decrease = (0x10000 + m_lastSequence - audioPacket.getSequence()) % 0x10000;
		if (increase == 1) {
			m_lastSequence = audioPacket.getSequence();
		}
		else if ((increase > 1) && (increase <= RetransmitRequestsOpenLimit)) {
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
