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

import java.util.logging.Logger;

import org.jboss.netty.channel.*;

public class RaopRtpTimingHandler extends SimpleChannelHandler {
	private static Logger s_logger = Logger.getLogger(RaopRtpTimingHandler.class.getName());

	public static final double TimeRequestInterval = 0.2;
	
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
				timingRequestPacket.getSendTime().setDouble(m_audioClock.getNowSecondsTime());
				
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
	private final RunningExponentialAverage m_remoteSecondsOffset = new RunningExponentialAverage();
//	private final RunningWeightedAverage m_remoteSecondsOffset = new RunningWeightedAverage((int)(10.0 / TimeRequestInterval));
	private Thread m_synchronizationThread;
	
	public RaopRtpTimingHandler(final AudioClock audioClock) {
		m_audioClock = audioClock;
	}
	
	@Override
	public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent evt)
		throws Exception
	{
		channelClosed(ctx, evt);
		
		if (m_synchronizationThread == null) {
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
		final double localReceiveSecondsTime = m_audioClock.getNowSecondsTime();
		
		final double localSecondsTime = 
			localReceiveSecondsTime * 0.5 +
			timingResponsePacket.getReferenceTime().getDouble() * 0.5;
		final double remoteSecondsTime =
			timingResponsePacket.getReceivedTime().getDouble() * 0.5 +
			timingResponsePacket.getSendTime().getDouble() * 0.5;
		final double remoteSecondsOffset = remoteSecondsTime - localSecondsTime;
		
		final double localInterval =
			localReceiveSecondsTime -
			timingResponsePacket.getReferenceTime().getDouble();
		final double remoteInterval =
			timingResponsePacket.getSendTime().getDouble() -
			timingResponsePacket.getReceivedTime().getDouble();
		final double transmissionTime = Math.max(localInterval - remoteInterval, 0);
		final double weight = 1e-4 / (transmissionTime + 1e-3);
		
		final double remoteSecondsOffsetPrevious = (!m_remoteSecondsOffset.isEmpty() ? m_remoteSecondsOffset.get() : 0.0);
		m_remoteSecondsOffset.add(remoteSecondsOffset, weight);
		final double secondsTimeAdjustment = m_remoteSecondsOffset.get() - remoteSecondsOffsetPrevious;
		
		s_logger.fine("Timing response with weight " + weight + " indicated offset " + remoteSecondsOffset + " thereby adjusting the averaged offset by " + secondsTimeAdjustment + " leading to the new averaged offset " + m_remoteSecondsOffset.get());
	}

	private synchronized void syncReceived(RaopRtpPacket.Sync syncPacket) {
		if (!m_remoteSecondsOffset.isEmpty()) {
			m_audioClock.setFrameTime(
				syncPacket.getTimeStampMinusLatency(),
				convertRemoteToLocalSecondsTime(syncPacket.getTime().getDouble())
			);
		}
		else {
			m_audioClock.setFrameTime(
				syncPacket.getTimeStampMinusLatency(),
				0.0
			);
			s_logger.warning("Times synchronized, cannot correct latency of sync packet");
		}
	}
	
	private double convertRemoteToLocalSecondsTime(double remoteSecondsTime) {
		return remoteSecondsTime - m_remoteSecondsOffset.get();
	}
}
