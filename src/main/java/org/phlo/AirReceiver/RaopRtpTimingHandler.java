package org.phlo.AirReceiver;

import java.util.Deque;
import java.util.logging.Logger;

import org.jboss.netty.channel.*;

public class RaopRtpTimingHandler extends SimpleChannelHandler {
	private static Logger s_logger = Logger.getLogger(RaopRtpTimingHandler.class.getName());

	public static final double SyncInternval = 0.1;
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
					Thread.sleep(Math.round(SyncInternval * 1000));
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}
	
	private final AudioClock m_audioClock;
	private Thread m_synchronizationThread;
	private final Deque<Double> m_lastDeltas = new java.util.LinkedList<Double>();
	private double m_delta = Double.NaN;
	
	public RaopRtpTimingHandler(final AudioClock audioClock) {
		m_audioClock = audioClock;
	}
	
	@Override
	public synchronized void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent evt)
		throws Exception
	{
		channelClosed(ctx, evt);
		
		m_synchronizationThread = new Thread(new TimingRequester(ctx.getChannel()));
		m_synchronizationThread.setDaemon(true);
		m_synchronizationThread.setName("Time Synchronizer");
		m_synchronizationThread.start();
		s_logger.fine("Time synchronizer started");
	}
	

	@Override
	public synchronized void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent evt)
		throws Exception
	{
		if (m_synchronizationThread != null) {
			while (m_synchronizationThread.isAlive()) {
				m_synchronizationThread.interrupt();
				try { Thread.sleep(10); }
				catch (InterruptedException e) { /* Ignore */ }
			}
			m_synchronizationThread = null;
			s_logger.fine("Time synchronizer stopped");
		}
	}

	@Override
	public synchronized void messageReceived(ChannelHandlerContext ctx, MessageEvent evt)
		throws Exception
	{
		if (evt.getMessage() instanceof RaopRtpPacket.Sync)
			syncReceived((RaopRtpPacket.Sync)evt.getMessage());
		else if (evt.getMessage() instanceof RaopRtpPacket.TimingResponse)
			timingResponseReceived((RaopRtpPacket.TimingResponse)evt.getMessage());

		super.messageReceived(ctx, evt);
	}

	private void timingResponseReceived(RaopRtpPacket.TimingResponse timingResponsePacket) {
		final double localSecondsTime = 
			m_audioClock.getNowLocalSecondsTime() * 0.5 +
			timingResponsePacket.getReferenceTime().getDouble() * 0.5;
		final double remoteSecondsTime =
			timingResponsePacket.getSendTime().getDouble() * 0.5 +
			timingResponsePacket.getReceivedTime().getDouble() * 0.5;
		addDelta(remoteSecondsTime - localSecondsTime);
	}

	private void syncReceived(RaopRtpPacket.Sync syncPacket) {
		double localSecondsTime = fromRemoteSecondsTime(syncPacket.getTimeLastSync().getDouble());
		if (Double.isNaN(localSecondsTime))
			localSecondsTime = m_audioClock.getNowLocalSecondsTime();
		
		m_audioClock.requestSyncRemoteFrameTime(
			syncPacket.getNowMinusLatency(),
			localSecondsTime,
			syncPacket.getExtension()
		);
	}
	
	private void addDelta(double delta) {
		m_lastDeltas.addLast(delta);
		while (m_lastDeltas.size() > DeltaCount)
			m_lastDeltas.removeFirst();
		
		double avg = 0.0;
		for(double d: m_lastDeltas)
			avg += d;
		avg /= m_lastDeltas.size();
		s_logger.fine("Delta between remote and local seconds time is now " + m_delta + " seconds after adjustment of " + (avg - m_delta) + " seconds");

		m_delta = avg;
	}
	
	private double fromRemoteSecondsTime(double remoteSecondsTime) {
		if (!Double.isNaN(m_delta))
			return remoteSecondsTime - m_delta;
		else
			return Double.NaN;
	}
}
