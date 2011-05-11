package org.phlo.AirReceiver;

import java.util.logging.Logger;

import org.jboss.netty.channel.*;
import org.phlo.AirReceiver.RaopRtpPacket.TimingResponse;

public class RaopRtpTimeSourceHandler extends SimpleChannelHandler {
	private static Logger s_logger = Logger.getLogger(RaopRtpTimeSourceHandler.class.getName());

	private Channel m_timingChannel;
	private Thread m_timingRequesterThread;
	
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
	public synchronized void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
		throws Exception
	{
		m_timingChannel = ctx.getChannel();
		m_timingRequesterThread = new Thread(new TimingRequester());
		m_timingRequesterThread.start();
		s_logger.fine("Timing channel connected, timing requester started");
	}
	

	@Override
	public synchronized void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
		throws Exception
	{
		while (m_timingRequesterThread.isAlive()) {
			m_timingRequesterThread.interrupt();
			Thread.yield();
		}
		s_logger.fine("Timing channel closed, timing requester stopped");
	}

	@Override
	public synchronized void messageReceived(ChannelHandlerContext ctx, MessageEvent evt)
		throws Exception
	{
		RaopRtpPacket.TimingResponse timingResponsePacket = (TimingResponse)evt.getMessage();
		
	}
}
