package org.phlo.AirReceiver;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.sampled.*;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;

public class RaopAudioPlaybackHandler extends SimpleChannelUpstreamHandler {
	private static Logger s_logger = Logger.getLogger(RaopAudioPlaybackHandler.class.getName());
	
	private class LineUnderrunPreventer implements Runnable {
		private static final double SafetyMarginSeconds = 0.01;
		private static final long SafetyMarginFrames = (long)(SafetyMarginSeconds * SampleRate);
		
		@Override
		public void run() {
			Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
			
			final byte[] silence = new byte[(int)(SafetyMarginFrames * 1.5) * BytesPerFrame];
			for(int i=0; i < silence.length; i += 2)
				silence[i] = -0x80;

			while (true) {
				if (!m_line.isOpen())
					break;
				if (!m_line.isRunning())
					Thread.yield();
				
				long sleepMilliseconds;
				synchronized(RaopAudioPlaybackHandler.this) {
					int lineTimeNow = m_line.getFramePosition();
					
					if ((m_lineTimeQueueEnd - lineTimeNow) < SafetyMarginFrames) 
						enqueue(silence);
					
					double queuedSeconds = (double)((m_lineTimeQueueEnd - lineTimeNow)) / (double)SampleRate;
					sleepMilliseconds = Math.round(1000.0 * (queuedSeconds - SafetyMarginSeconds));
				}
				
				try {
					Thread.sleep(sleepMilliseconds);
				}
				catch (InterruptedException e) {
					/* Ignore */
				}
			}
			
			s_logger.info("Audio output line was closed, buffer underrun preventer exits");
		}
	}
	
	private static final int SampleRate = 44100;
	private static final int BitsPerSample = 16;
	private static final int Channels = 2;
	private static final int BytesPerFrame = BitsPerSample * Channels / 8;
	
	private static 	AudioFormat s_audioFormat = new AudioFormat(
		SampleRate /* sample rate */,
		BitsPerSample /* bits per sample */,
		Channels /* number of channels */,
		true /* signed, unsigned is not supported, on OSX at least */,
		true /* big endian */
	);

	private final SourceDataLine m_line;
	private long m_expectedSequence = -1;
	private long m_lineTimeQueueEnd = 0;
	
	public RaopAudioPlaybackHandler()
		throws LineUnavailableException
	{
		DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, s_audioFormat);
		m_line = (SourceDataLine)AudioSystem.getLine(lineInfo);
		m_line.open(s_audioFormat);
		m_line.start();
		
		assert m_line.isOpen();
		(new Thread(new LineUnderrunPreventer())).start();
		assert m_line.getLongFramePosition() == 0;
	}
	
	@Override
	public synchronized void messageReceived(ChannelHandlerContext ctx, MessageEvent evt)
		throws Exception
	{
		if (evt.getMessage() instanceof RaopRtpPacket.Audio)
			audioReceived(ctx, (RaopRtpPacket.Audio)evt.getMessage());
		else if (evt.getMessage() instanceof RaopRtpPacket.Sync)
			syncReceived(ctx, (RaopRtpPacket.Sync)evt.getMessage());
		else
			super.messageReceived(ctx, evt);
	}

	
	public void syncReceived(ChannelHandlerContext ctx, RaopRtpPacket.Sync syncPacket) {
		/* Ignore */
	}
	
	public void audioReceived(ChannelHandlerContext ctx, RaopRtpPacket.Audio audioPacket)
	{
		ChannelBuffer audioPayload = audioPacket.getPayload();
		
		/* Verify sequence number */
		if (m_expectedSequence > audioPacket.getSequence()) {
			/* Ignore old packets */
			s_logger.warning(
				"Packet sequence number " + audioPacket.getSequence() + " " +
				"is smaller than expected sequence number " + m_expectedSequence + ", " +
				"dropping packet"
			);
			return;
		}
		else if ((m_expectedSequence >= 0) && (audioPacket.getSequence() > m_expectedSequence)) {
			/* Complain about missing packets */
			s_logger.warning(
				"Packet sequence number " + audioPacket.getSequence() + " " +
				"is larger than expected sequence number " + m_expectedSequence + ", " +
				(audioPacket.getSequence() - m_expectedSequence) + " packets missing"
			);
		}
		m_expectedSequence = (audioPacket.getSequence() + 1) % 0x10000;
		
		/* Extract PCM samples */
		byte[] pcm = new byte[audioPayload.capacity()];
		audioPayload.getBytes(0, pcm);
		
		/* The line expects signed PCM samples, so we must
		 * convert the unsigned PCM samples to signed.
		 * Note that this only affects the high bytes,
		 * hence the "i += 2"!
		 */
		for(int i=0; i < pcm.length; i += 2)
			pcm[i] = (byte)((pcm[i] & 0xff) - 0x80);
		
		/* Enqueue samples */
		enqueue(pcm);
	}
	
	private void enqueue(byte[] pcm) {
		int framesWritten = m_line.write(pcm, 0, pcm.length) / BytesPerFrame;
		m_lineTimeQueueEnd += framesWritten;
		
		if (s_logger.isLoggable(Level.FINEST))
			s_logger.finest("Queued " + framesWritten + " additional PCM frames, queue now contains " + (m_lineTimeQueueEnd - m_line.getFramePosition()) + " frames");
	}
}
