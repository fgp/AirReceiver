package org.phlo.AirReceiver;

import java.util.logging.Logger;

import org.jboss.netty.channel.*;
import org.phlo.AirReceiver.RaopRtpPacket.TimingResponse;

public class RaopRtpTimeSourceHandler extends SimpleChannelHandler {
	private static Logger s_logger = Logger.getLogger(RaopRtpTimeSourceHandler.class.getName());

	private Channel m_timingChannel;
	private Thread m_synchronizationThread;
	
	public RaopRtpTimeSourceHandler() {
		
	}
	
	private double getNowNtpTime() {
		double nowNtpTime = 0x83aa7e80L; /* Unix epoch as NTP time */
		nowNtpTime += (double)System.currentTimeMillis() / 1000.0;
		return nowNtpTime;
	}
	
	private class TimingRequester implements Runnable {
		@Override
		public void run() {
			while (Thread.currentThread().isInterrupted()) {
				RaopRtpPacket.TimingRequest timingRequestPacket = new RaopRtpPacket.TimingRequest();
				timingRequestPacket.getReceivedTime().setDouble(0);
				timingRequestPacket.getReferenceTime().setDouble(0);
				timingRequestPacket.getSendTime().setDouble(getNowNtpTime());
				
				m_timingChannel.write(timingRequestPacket);
				try {
					Thread.sleep(1000);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}
	
	@Override
	public synchronized void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent evt)
		throws Exception
	{
		m_timingChannel = ctx.getChannel();
		m_synchronizationThread = new Thread(new TimingRequester());
		m_synchronizationThread.setDaemon(true);
		m_synchronizationThread.setName("Time Synchronizer");
		m_synchronizationThread.start();
		s_logger.fine("Tike source synchronizer started");
	}
	

	@Override
	public synchronized void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent evt)
		throws Exception
	{
		while (m_synchronizationThread.isAlive()) {
			m_synchronizationThread.interrupt();
			try { Thread.sleep(10); }
			catch (InterruptedException e) { /* Ignore */ }
		}
		s_logger.fine("Time source synchronizer stopped");
	}

	@Override
	public synchronized void messageReceived(ChannelHandlerContext ctx, MessageEvent evt)
		throws Exception
	{
		RaopRtpPacket.TimingResponse timingResponsePacket = (TimingResponse)evt.getMessage();
		
		super.messageReceived(ctx, evt);
	}
}
