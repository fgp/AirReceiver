package org.phlo.AirReceiver;

import java.util.logging.Logger;

import org.jboss.netty.channel.*;

public class RaopRtpTimingHandler extends SimpleChannelHandler {
	private static Logger s_logger = Logger.getLogger(RaopRtpTimingHandler.class.getName());

	private class TimingRequester implements Runnable {
		private final Channel m_channel;
		
		public TimingRequester(final Channel channel) {
			m_channel = channel;
		}
		
		@Override
		public void run() {
			while (Thread.currentThread().isInterrupted()) {
				RaopRtpPacket.TimingRequest timingRequestPacket = new RaopRtpPacket.TimingRequest();
				timingRequestPacket.getReceivedTime().setDouble(0);
				timingRequestPacket.getReferenceTime().setDouble(0);
				timingRequestPacket.getSendTime().setDouble(getNowNtpTime());
				
				m_channel.write(timingRequestPacket);
				try {
					Thread.sleep(100);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}
	
	private final AudioOutputQueue m_audioOutputQueue;
	private Thread m_synchronizationThread;
	
	public RaopRtpTimingHandler(final AudioOutputQueue audioOutputQueue) {
		m_audioOutputQueue = audioOutputQueue;
	}
	
	private double getNowNtpTime() {
		double nowNtpTime = 0x83aa7e80L; /* Unix epoch as NTP time */
		nowNtpTime += (double)System.currentTimeMillis() / 1000.0;
		return nowNtpTime;
	}
	
	
	@Override
	public synchronized void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent evt)
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
	}

	private void syncReceived(RaopRtpPacket.Sync syncPacket) {
		m_audioOutputQueue.sync(
			syncPacket.getNowMinusLatency(),
			m_audioOutputQueue.getNowFrameTime(),
			syncPacket.getExtension()
		);
	}
}
