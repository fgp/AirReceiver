package org.phlo.AirReceiver;

import java.util.Deque;
import java.util.Iterator;
import java.util.logging.Logger;

import org.jboss.netty.channel.*;

public class RaopRtpTimingHandler extends SimpleChannelHandler {
	private static Logger s_logger = Logger.getLogger(RaopRtpTimingHandler.class.getName());

	public static final double TimeRequestInterval = 0.1;
	public static final int DeltaCount = 128;
	
	private class TimingRequester implements Runnable {
		private final Channel m_channel;
		
		public TimingRequester(final Channel channel) {
			m_channel = channel;
		}
		
		@Override
		public void run() {
			while (!Thread.currentThread().isInterrupted()) {
				RaopRtpPacket.TimingRequest timingRequestPacket = new RaopRtpPacket.TimingRequest();
				timingRequestPacket.getReceivedTime().setDouble(0);
				timingRequestPacket.getReferenceTime().setDouble(0);
				timingRequestPacket.getSendTime().setDouble(m_audioClock.getNowLocalSecondsTime());
				
				m_channel.write(timingRequestPacket);
				try {
					Thread.sleep(Math.round(TimeRequestInterval * 1000));
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}
	
	private final AudioClock m_audioClock;
	private final Deque<Double> m_deltaWeights = new java.util.LinkedList<Double>();
	private final Deque<Double> m_deltaValues = new java.util.LinkedList<Double>();
	private double m_delta = Double.NaN;
	private Thread m_synchronizationThread;
	
	public RaopRtpTimingHandler(final AudioClock audioClock) {
		m_audioClock = audioClock;
	}
	
	@Override
	public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent evt)
		throws Exception
	{
		channelClosed(ctx, evt);
		
		synchronized(this) {
			m_synchronizationThread = new Thread(new TimingRequester(ctx.getChannel()));
			m_synchronizationThread.setDaemon(true);
			m_synchronizationThread.setName("Time Synchronizer");
			m_synchronizationThread.start();
			s_logger.fine("Time synchronizer started");
		}
		
		super.channelOpen(ctx, evt);
	}
	

	@Override
	public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent evt)
		throws Exception
	{
		synchronized(this) {
			if (m_synchronizationThread != null)
				m_synchronizationThread.interrupt();
		}
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent evt)
		throws Exception
	{
		if (evt.getMessage() instanceof RaopRtpPacket.Sync)
			syncReceived((RaopRtpPacket.Sync)evt.getMessage());
		else if (evt.getMessage() instanceof RaopRtpPacket.TimingResponse)
			timingResponseReceived((RaopRtpPacket.TimingResponse)evt.getMessage());

		super.messageReceived(ctx, evt);
	}

	private synchronized void timingResponseReceived(RaopRtpPacket.TimingResponse timingResponsePacket) {
		final double localSecondsTime = 
			m_audioClock.getNowLocalSecondsTime() * 0.5 +
			timingResponsePacket.getReferenceTime().getDouble() * 0.5;
		final double remoteSecondsTime =
			timingResponsePacket.getReceivedTime().getDouble() * 0.5 +
			timingResponsePacket.getSendTime().getDouble() * 0.5;
		final double delta = remoteSecondsTime - localSecondsTime;
		
		final double localInterval =
			m_audioClock.getNowLocalSecondsTime() -
			timingResponsePacket.getReferenceTime().getDouble();
		final double remoteInterval =
			timingResponsePacket.getSendTime().getDouble() -
			timingResponsePacket.getReceivedTime().getDouble();
		final double transmissionTime = Math.max(localInterval - remoteInterval, 0);
		final double weight = 10e-3 / (transmissionTime + 10e-3);
		
		addDelta(delta, weight);		
		s_logger.fine("Timing response indicated delta " + delta + " and had transmission time " + transmissionTime + ", weighting with " + weight);
	}

	private synchronized void syncReceived(RaopRtpPacket.Sync syncPacket) {
		double localSecondsTime = fromRemoteSecondsTime(syncPacket.getTime().getDouble());
		if (Double.isNaN(localSecondsTime))
			localSecondsTime = m_audioClock.getNowLocalSecondsTime();		
		final double syncAge = Math.abs(m_audioClock.getNowLocalSecondsTime() - localSecondsTime);
			
		m_audioClock.requestSyncRemoteFrameTime(
			syncPacket.getTimeStampMinusLatency(),
			localSecondsTime,
			syncPacket.getExtension()
		);
		s_logger.info("Sync packet with timestamp(-latency) " + syncPacket.getTimeStampMinusLatency() + " and local time " + localSecondsTime + " was " + syncAge + " seconds old");
	}
	
	private void addDelta(double delta, double weight) {
		m_deltaValues.addLast(delta);
		m_deltaWeights.addLast(weight);
		while (m_deltaValues.size() > DeltaCount)
			m_deltaValues.removeFirst();
		while (m_deltaWeights.size() > DeltaCount)
			m_deltaWeights.removeFirst();
		
		double vsum = 0.0;
		double wsum = 0.0;
		final Iterator<Double> i_v = m_deltaValues.iterator();
		final Iterator<Double> i_w = m_deltaWeights.iterator();
		while ((i_v.hasNext() && (i_w.hasNext()))) {
			final double v = i_v.next();
			final double w = i_w.next();
			vsum += v*w;
			wsum += w;
		}
		double avg = vsum / wsum;

		s_logger.fine("Delta between remote and local seconds time is now " + avg + " seconds after adjustment of " + (avg - m_delta) + " seconds");
		m_delta = avg;
	}
	
	private double fromRemoteSecondsTime(double remoteSecondsTime) {
		if (!Double.isNaN(m_delta))
			return remoteSecondsTime - m_delta;
		else
			return Double.NaN;
	}
}
