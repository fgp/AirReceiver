package org.phlo.AirReceiver;

import java.util.Arrays;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.sampled.*;

public class AudioOutputQueue {
	private static Logger s_logger = Logger.getLogger(AudioOutputQueue.class.getName());

	private static final double BufferSizeSeconds = 0.05;
	private static final double BufferSafetyMarginSeconds= 0.02;
	
	private final AudioFormat m_format;
	private final boolean m_convertUnsignedToSigned;
	private final int m_bytesPerFrame;
	private final SourceDataLine m_line;
	private final byte[] m_lineLastFrame;
	private final ConcurrentSkipListMap<Long, byte[]> m_queue = new ConcurrentSkipListMap<Long, byte[]>();
	private final Thread m_queueThread = new Thread(new EnQueuer());

	private double m_lineStopWallTime;
	private long m_lineFramesMissed;
	private long m_lineFramesWritten;
	
	private long m_remoteFrameTimeOffset;
	
	private class EnQueuer implements Runnable, LineListener {
		@Override
		public void run() {
			try {
				Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

				synchronized(AudioOutputQueue.this) {
					Arrays.fill(m_lineLastFrame, (byte)0);
					m_lineStopWallTime = -1;
					m_lineFramesMissed = 0;
					m_lineFramesWritten = 0;
				}

				m_line.addLineListener(this);
				m_line.start();
				
				while (!Thread.currentThread().isInterrupted()) {
					while (!Thread.currentThread().isInterrupted()) {
						long nextPlaybackTimeGap = Long.MAX_VALUE;
						
						if (getBufferedSeconds() == 0)
							s_logger.warning("Audio output line has underrun");
						
						if (getBufferedSeconds() >= BufferSafetyMarginSeconds)
							break;
						
						if (!m_queue.isEmpty()) {
							final long nextPlaybackRemoteTime = m_queue.firstKey();
							final long nextPlaybackFrameTime = fromRemoteFrameTime(nextPlaybackRemoteTime);
							nextPlaybackTimeGap = nextPlaybackFrameTime - getEndFrameTime();
							
							if (nextPlaybackTimeGap <= 0) {
								final byte[] nextPlaybackSamples = m_queue.get(nextPlaybackRemoteTime);
								s_logger.finest("Audio data containing " + nextPlaybackSamples.length / m_bytesPerFrame + " frames for playback time " + nextPlaybackFrameTime + " found in queue, appending to the output line");
								appendFrames(nextPlaybackSamples, 0, nextPlaybackSamples.length, nextPlaybackFrameTime, true);
								m_queue.remove(nextPlaybackRemoteTime);

								continue;
							}
						}
						
						final long silenceFrames = Math.min(
							Math.round((BufferSafetyMarginSeconds * 1.2 - getBufferedSeconds()) *
							(double)m_format.getSampleRate()),
							nextPlaybackTimeGap
						);
						appendFrames(null, 0, 0, getEndFrameTime() + silenceFrames, false);
						if (nextPlaybackTimeGap < Long.MAX_VALUE)
							s_logger.warning("Audio output line about to underrun since " + nextPlaybackTimeGap + " frames appear to be missing , appended " + silenceFrames + " frames of silence");
					}
					
					long sleepNanos = Math.round(
						1e9 *
						Math.max(
							getBufferedSeconds() - BufferSafetyMarginSeconds * 0.8,
							BufferSafetyMarginSeconds * 0.2
						)
					);
					try {
						Thread.sleep(sleepNanos / 1000000, (int)(sleepNanos % 1000000));
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
			}
			catch (Throwable e) {
				s_logger.log(Level.SEVERE, "Audio output thread died unexpectedly", e);
			}
			finally {
				m_line.removeLineListener(this);
			}
		}
		
		public void appendFrames(byte[] samples, int off, int len, long time, boolean warnNonContinous) {
			while (true) {
				long endFrameTime = getEndFrameTime();
				
				if (endFrameTime == time) {
					appendFrames(samples, off, len);
					break;
				}
				else if (endFrameTime < time) {
					if (warnNonContinous)
						s_logger.warning("Audio output non-continous (end of line is " + endFrameTime + " but requested playback time is  " + time + "), writing " + (time - endFrameTime) + " frames of silence");
					byte[] silenceFrames = new byte[(int)(time - endFrameTime) * m_bytesPerFrame];
					for(int i = 0; i < silenceFrames.length; ++i)
						silenceFrames[i] = m_lineLastFrame[i % m_bytesPerFrame];
					appendFrames(silenceFrames, 0, silenceFrames.length);
				}
				else if (endFrameTime > time) {
					if (warnNonContinous)
						s_logger.warning("Audio output non-continous (end of line is " + endFrameTime + " but requested playback time is  " + time + "), skipping " + (endFrameTime - time) + " frames");
					off += endFrameTime - time;
					time += endFrameTime - time;
				}
				else
					assert false;
			}
		}
		
		public void appendFrames(byte[] samples, int off, int len) {
			assert len % m_bytesPerFrame == 0;

			off = Math.min(off, (samples != null) ? samples.length : 0);
			len = Math.min(len, (samples != null) ? samples.length - off : 0);

			if (len <= 0)
				return;
			
			byte[] samplesConverted = Arrays.copyOfRange(samples, off, off+len);
			if (m_convertUnsignedToSigned) {
				/* The line expects signed PCM samples, so we must
				 * convert the unsigned PCM samples to signed.
				 * Note that this only affects the high bytes!
				 */
				for(int i=0; i < samplesConverted.length; i += 2)
					samplesConverted[i] = (byte)((samplesConverted[i] & 0xff) - 0x80);
			}
			
			int bytesWritten = m_line.write(samplesConverted, 0, samplesConverted.length);
			if (bytesWritten != len)
				s_logger.warning("Audio output line accepted only " + bytesWritten + " bytes of sample data while trying to write " + samples.length + " bytes");
			
			synchronized(AudioOutputQueue.this) {
				m_lineFramesWritten += bytesWritten / m_bytesPerFrame;
				for(int b=0; b < m_bytesPerFrame; ++b)
					m_lineLastFrame[b] = samples[off + len - (m_bytesPerFrame - b)];
				
				s_logger.finest("Audio output line end is now at " + getEndFrameTime() + " after writing " + len / m_bytesPerFrame + " frames");
			}
		}

		@Override
		public void update(LineEvent event) {
			assert event.getLine() == m_line;
			
			if (event.getType() == LineEvent.Type.START)
				onLineStart(event);
			else if (event.getType() == LineEvent.Type.STOP)
				onLineStop(event);
		}

		private void onLineStop(LineEvent event) {
			double lineStopWallTime = getNowWallTime();
			s_logger.warning("Audio output line stopped prematurely at wall time " + lineStopWallTime);

			synchronized(AudioOutputQueue.this) {
				m_lineStopWallTime = lineStopWallTime;
			}
		}

		private void onLineStart(LineEvent event) {
			if (m_lineStopWallTime < 0)
				return;
			
			double nowWallTime = getNowWallTime();
			long framesSinceStop = getFramesSinceStop(nowWallTime);
			s_logger.info("Audio output line started at wall time " + nowWallTime + ", " + framesSinceStop + " frames after it stopped");

			synchronized(AudioOutputQueue.this) {
				m_lineFramesMissed += framesSinceStop;
				m_lineStopWallTime = -1;
			}
		}
	}
	
	AudioOutputQueue(final AudioFormat format) throws LineUnavailableException {
		if (AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())) {
			m_format = format;
			m_convertUnsignedToSigned = false;
		}
		else if (AudioFormat.Encoding.PCM_UNSIGNED.equals(format.getEncoding())) {
			m_format = new AudioFormat(
				format.getSampleRate(),
				format.getSampleSizeInBits(),
				format.getChannels(),
				true,
				format.isBigEndian()
			);
			m_convertUnsignedToSigned = true;
		}
		else {
			throw new LineUnavailableException("Audio encoding " + format.getEncoding() + " is not supported");
		}
		
		m_bytesPerFrame = m_format.getChannels() * m_format.getSampleSizeInBits() / 8;
		m_lineLastFrame = new byte[m_bytesPerFrame];
		
		int desiredbufferSize = (int)Math.pow(2, Math.ceil(Math.log(BufferSizeSeconds * m_format.getSampleRate() * m_bytesPerFrame) / Math.log(2.0)));
		DataLine.Info lineInfo = new DataLine.Info(
			SourceDataLine.class,
			m_format,
			desiredbufferSize
		);
		m_line = (SourceDataLine)AudioSystem.getLine(lineInfo);
		m_line.open(m_format);
		s_logger.info("Audio output line created and openend. Requested buffer of " + desiredbufferSize / m_bytesPerFrame  + " frames, got " + m_line.getBufferSize() / m_bytesPerFrame + " frames");
		
		m_queueThread.start();
		try {
			while (!m_line.isActive())
				Thread.sleep(10);
		}
		catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			close();
		}
	}
	
	public void close() {
		while (m_queueThread.isAlive()) {
			m_queueThread.interrupt();
			Thread.yield();
		}
		m_line.stop();
		m_line.close();
	}
	
	public void enqueue(long playbackRemoteFrameTime, byte[] playbackSamples) {
		m_queue.put(playbackRemoteFrameTime, playbackSamples);
	}
	
	public void flush() {
		m_queue.clear();
	}
	
	public synchronized long toRemoteFrameTime(long frameTime) {
		return frameTime + m_remoteFrameTimeOffset;
	}
	
	public synchronized long fromRemoteFrameTime(long remoteFrameTime) {
		return remoteFrameTime - m_remoteFrameTimeOffset;
	}
	
	public synchronized void sync(long nowRemoteFrameTime) {
		m_remoteFrameTimeOffset = nowRemoteFrameTime - getNowFrameTime();
		s_logger.info("Remote frame time to local frame time offset is now " + m_remoteFrameTimeOffset);
	}
	
	private double getNowWallTime() {
		return (double)System.nanoTime() / 1e9;
	}
	
	private synchronized long getFramesSinceStop(double nowWallTime) {
		if (m_lineStopWallTime >= 0)
			return Math.round((double)m_format.getSampleRate() * (getNowWallTime() - m_lineStopWallTime));
		else
			return 0;
	}
	
	private synchronized long getNowFrameTime() {
		return m_line.getLongFramePosition() + m_lineFramesMissed + getFramesSinceStop(getNowWallTime());
	}
	
	private synchronized long getEndFrameTime() {
		return m_lineFramesWritten + m_lineFramesMissed + getFramesSinceStop(getNowWallTime());
	}
	
	private synchronized double getBufferedSeconds() {
		return (double)(m_lineFramesWritten - m_line.getLongFramePosition()) / m_format.getSampleRate();
	}
}
