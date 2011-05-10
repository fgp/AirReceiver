package org.phlo.AirReceiver;

import java.util.Arrays;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.sampled.*;

public class AudioOutputQueue {
	private static Logger s_logger = Logger.getLogger(AudioOutputQueue.class.getName());

	private static double LineBufferSeconds = 0.05;
	private static double LineBufferSafetyMarginSeconds = 0.02;
	
	private final AudioFormat m_format;
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
				
				mainLoop:
				while (!Thread.interrupted()) {
					while (!Thread.interrupted()) {
						long nextPlaybackTimeGap = Long.MAX_VALUE;
						
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
						
						if (getBufferedSeconds() >= LineBufferSafetyMarginSeconds)
							break;
						
						final long silenceFrames = Math.min(
							Math.round((LineBufferSafetyMarginSeconds * 1.5 - getBufferedSeconds()) *
							(double)m_format.getSampleRate()),
							nextPlaybackTimeGap
						);
						appendFrames(null, 0, 0, getEndFrameTime() + silenceFrames, false);
						s_logger.warning("Audio output line about to underrun since " + nextPlaybackTimeGap + " frames appear to be missing , appended " + silenceFrames + " frames of silence");
					}
					
					long sleepNanos = Math.round(
						1e9 *
						Math.max(
							getBufferedSeconds() - LineBufferSafetyMarginSeconds,
							LineBufferSafetyMarginSeconds / 10.0
						)
					);
					try {
						Thread.sleep(sleepNanos / 1000000, (int)(sleepNanos % 1000000));
					}
					catch (InterruptedException e) {
						break mainLoop;
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
			
			/* The line expects signed PCM samples, so we must
			 * convert the unsigned PCM samples to signed.
			 * Note that this only affects the high bytes!
			 */
			byte[] samplesSigned = Arrays.copyOfRange(samples, off, off+len);
			for(int i=0; i < samplesSigned.length; i += 2)
				samplesSigned[i] = (byte)((samplesSigned[i] & 0xff) - 0x80);
			
			int bytesWritten = m_line.write(samplesSigned, 0, samplesSigned.length);
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
	
	AudioOutputQueue(final AudioFormat format) throws LineUnavailableException, InterruptedException {
		m_format = format;
		m_bytesPerFrame = m_format.getChannels() * m_format.getSampleSizeInBits() / 8;
		m_lineLastFrame = new byte[m_bytesPerFrame];
		
		DataLine.Info lineInfo = new DataLine.Info(
			SourceDataLine.class,
			m_format,
			(int)Math.round(LineBufferSeconds * m_format.getSampleRate()) * m_bytesPerFrame
		);
		m_line = (SourceDataLine)AudioSystem.getLine(lineInfo);
		m_line.open(m_format);
		
		m_queueThread.start();
		try {
			while (!m_line.isActive())
				Thread.sleep(10);
		}
		catch(InterruptedException e) {
			close();
			throw e;
		}
	}
	
	public void close() throws InterruptedException {
		m_queueThread.interrupt();
		try {
			m_queueThread.join();
		}
		catch (InterruptedException e) {
			s_logger.log(Level.WARNING, "Audio queue interrupted while waiting for queue thread to finish", e);
			throw e;
		}
		finally {
			m_line.stop();
			m_line.close();
		}
	}
	
	public synchronized void enqueue(long playbackRemoteFrameTime, byte[] playbackSamples) {
		m_queue.put(playbackRemoteFrameTime, playbackSamples);
	}
	
	public synchronized long toRemoteFrameTime(long frameTime) {
		return frameTime + m_remoteFrameTimeOffset;
	}
	
	public synchronized long fromRemoteFrameTime(long remoteFrameTime) {
		return remoteFrameTime - m_remoteFrameTimeOffset;
	}
	
	public synchronized void sync(long nowRemoteFrameTime) {
		m_remoteFrameTimeOffset = nowRemoteFrameTime - getNowFrameTime() - (int)m_format.getSampleRate();
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
		return m_line.getFramePosition() + m_lineFramesMissed + getFramesSinceStop(getNowWallTime());
	}
	
	private synchronized long getEndFrameTime() {
		return m_lineFramesWritten + m_lineFramesMissed + getFramesSinceStop(getNowWallTime());
	}
	
	private synchronized double getBufferedSeconds() {
		return (double)((m_line.getBufferSize() - m_line.available()) / m_bytesPerFrame) / (double)m_format.getSampleRate();
	}
}
