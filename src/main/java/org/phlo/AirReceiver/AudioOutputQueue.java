package org.phlo.AirReceiver;

import java.util.Arrays;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.sampled.*;

public class AudioOutputQueue implements AudioClock {
	private static Logger s_logger = Logger.getLogger(AudioOutputQueue.class.getName());

	private static final double QueueLengthMaxSeconds = 10;
	private static final double BufferSizeSeconds = 0.05;
	private static final double TimingPrecision = 0.001;

	/**
	 * Signals that the queue is being closed.
	 * Never transitions from true to false!
	 */
	private volatile boolean m_closing = false;
	
	/**
	 *  The line's audio format
	 */
	private final AudioFormat m_format;
	
	/**
	 * True if the line's audio format is signed but
	 * the requested format was unsigned
	 */
	private final boolean m_convertUnsignedToSigned;
	
	/**
	 * Bytes per frame, i.e. number of bytes
	 * per sample times the number of channels 
	 */
	private final int m_bytesPerFrame;
	
	/**
	 * Sample rate
	 */
	private final double m_sampleRate;
	
	/**
	 * Average packet size in frames.
	 * We use this as the number of silence frames
	 * to write on a queue underrun
	 */
	private final int m_packetSizeFrames;
	
	/**
	 * JavaSounds audio output line
	 */
	private final SourceDataLine m_line;
	
	/**
	 * The last frame written to the line.
	 * Used to generate filler data
	 */
	private final byte[] m_lineLastFrame;
	
	/**
	 * Packet queue, indexed by playback time
	 */
	private final ConcurrentSkipListMap<Long, byte[]> m_queue = new ConcurrentSkipListMap<Long, byte[]>();
	
	/**
	 * Enqueuer thread
	 */
	private final Thread m_queueThread = new Thread(new EnQueuer());

	/**
	 * Number of frames appended to the line
	 */
	private long m_lineFramesWritten = 0;
	
	/**
	 * Largest frame time seen so far
	 */
	private long m_latestSeenFrameTime = 0;
	
	/**
	 * The frame time corresponding to line time zero
	 */
	private long m_frameTimeOffset = 0;
	
	/**
	 * The seconds time corresponding to line time zero
	 */
	private final double m_secondsTimeOffset;
	
	/**
	 * Requested line gain
	 */
	private float m_requestedGain = 0.0f;
	
	/**
	 * Enqueuer thread
	 */
	private class EnQueuer implements Runnable {
		/**
		 * Enqueuer thread main method
		 */
		@Override
		public void run() {
			try {
				/* Mute line initially to prevent clicks */
				setLineGain(Float.NEGATIVE_INFINITY);

				/* Enqueue some silence */
				appendSilence(m_line.available() / m_bytesPerFrame);
				
				/* Start the line */
				m_line.start();
				
				boolean lineMuted = true;
				boolean didWarnGap = false;
				while (!m_closing) {
					if (!m_queue.isEmpty()) {
						/* Queue filled */
						
						/* If the gap between the next packet and the end of line is
						 * negligible (less than one packet), we write it to the line.
						 * Otherwise, we fill the line buffer with silence and hope for
						 * further packets to appear in the queue
						 */
						final long entryFrameTime = m_queue.firstKey();
						final long entryLineTime = convertFrameToLineTime(entryFrameTime);
						final long gapFrames = entryLineTime - getEndLineTime();
						if (gapFrames < m_packetSizeFrames) {
							/* Negligible gap between packet and line end. Prepare packet for playback */
							didWarnGap = false;
							
							/* Unmute line in case it was muted previously */
							if (lineMuted) {
								lineMuted = false;
								applyGain();
								s_logger.info("Audio data available, un-muted line");
							}
							
							/* Get sample data and do sanity checks */
							final byte[] nextPlaybackSamples = m_queue.remove(entryFrameTime);
							int nextPlaybackSamplesLength = nextPlaybackSamples.length;
							if (nextPlaybackSamplesLength % m_bytesPerFrame != 0) {
								s_logger.severe("Audio data contains non-integral number of frames, ignore last " + (nextPlaybackSamplesLength % m_bytesPerFrame) + " bytes");
								nextPlaybackSamplesLength -= nextPlaybackSamplesLength % m_bytesPerFrame;
							}
							
							/* Append packet to line */
							s_logger.finest("Audio data containing " + nextPlaybackSamplesLength / m_bytesPerFrame + " frames for playback time " + entryFrameTime + " found in queue, appending to the output line");
							appendFrames(nextPlaybackSamples, 0, nextPlaybackSamplesLength, entryLineTime);
							continue;
						}
						else {
							/* Gap between packet and line end. Warn */
							
							if (!didWarnGap) {
								didWarnGap = true;
								s_logger.warning("Audio data missing for frame time " + getEndLineTime() + " (currently " + gapFrames + " frames), writing " + m_packetSizeFrames + " frames of silence");							
							}
						}
					}
					else {
						/* Queue empty */
						
						if (!lineMuted) {
							lineMuted = true;
							setLineGain(Float.NEGATIVE_INFINITY);
							s_logger.fine("Audio data ended at frame time " + getEndLineTime() + ", writing " + m_packetSizeFrames + " frames of silence and muted line");
						}
					}
					
					appendSilence(m_packetSizeFrames);
				}
				
				/* Before we exit, we fill the line's buffer with silence. This should prevent
				 * noise from being output while the line is being stopped
				 */
				appendSilence(m_line.available() / m_bytesPerFrame);
			}
			catch (Throwable e) {
				s_logger.log(Level.SEVERE, "Audio output thread died unexpectedly", e);
			}
			finally {
				setLineGain(Float.NEGATIVE_INFINITY);
				m_line.stop();
				m_line.close();
			}
		}
		
		/**
		 * Append the range [off,off+len) from the provided sample data to the line.
		 * If the requested playback time does not match the line end time, samples are
		 * skipped or silence is inserted as necessary. If the data is marked as being
		 * just a filler, some warnings are suppressed.
		 * 
		 * @param samples sample data
		 * @param off sample data offset
		 * @param len sample data length
		 * @param time playback time
		 * @param warnNonContinous warn about non-continous samples
		 */
		private void appendFrames(byte[] samples, int off, int len, long lineTime) {
			assert off % m_bytesPerFrame == 0;
			assert len % m_bytesPerFrame == 0;
			
			while (true) {
				/* Fetch line end time only once per iteration */
				long endLineTime = getEndLineTime();
				
				final long timingErrorFrames = lineTime - endLineTime;
				final double timingErrorSeconds = (double)timingErrorFrames / m_sampleRate;
				
				if (Math.abs(timingErrorSeconds) <= TimingPrecision) {
					/* Samples to append scheduled exactly at line end. Just append them and be done */

					appendFrames(samples, off, len);
					break;
				}
				else if (timingErrorFrames > 0) {
					/* Samples to append scheduled after the line end. Fill the gap with silence */
					s_logger.warning("Audio output non-continous (gap of " + timingErrorFrames + " frames), filling with silence");

					appendSilence((int)(lineTime - endLineTime));
				}
				else if (timingErrorFrames < 0) {
					/* Samples to append scheduled before the line end. Remove the overlapping
					 * part and retry
					 */
					s_logger.warning("Audio output non-continous (overlap of " + (-timingErrorFrames) + "), skipping overlapping frames");

					off += (endLineTime - lineTime) * m_bytesPerFrame;
					lineTime += endLineTime - lineTime;
				}
				else {
					/* Strange universe... */
					assert false;
				}
			}
		}
		
		private void appendSilence(int frames) {
			byte[] silenceFrames = new byte[frames * m_bytesPerFrame];
			for(int i = 0; i < silenceFrames.length; ++i)
				silenceFrames[i] = m_lineLastFrame[i % m_bytesPerFrame];
			appendFrames(silenceFrames, 0, silenceFrames.length);
		}
		
		/**
		 * Append the range [off,off+len) from the provided sample data to the line.
		 * 
		 * @param samples sample data
		 * @param off sample data offset
		 * @param len sample data length
		 */
		private void appendFrames(byte[] samples, int off, int len) {
			assert off % m_bytesPerFrame == 0;
			assert len % m_bytesPerFrame == 0;

			/* Make sure that [off,off+len) does not exceed sample's bounds */
			off = Math.min(off, (samples != null) ? samples.length : 0);
			len = Math.min(len, (samples != null) ? samples.length - off : 0);
			if (len <= 0)
				return;
			
			/* Convert samples if necessary */
			byte[] samplesConverted = Arrays.copyOfRange(samples, off, off+len);
			if (m_convertUnsignedToSigned) {
				/* The line expects signed PCM samples, so we must
				 * convert the unsigned PCM samples to signed.
				 * Note that this only affects the high bytes!
				 */
				for(int i=0; i < samplesConverted.length; i += 2)
					samplesConverted[i] = (byte)((samplesConverted[i] & 0xff) - 0x80);
			}
			
			/* Write samples to line */
			int bytesWritten = m_line.write(samplesConverted, 0, samplesConverted.length);
			if (bytesWritten != len)
				s_logger.warning("Audio output line accepted only " + bytesWritten + " bytes of sample data while trying to write " + samples.length + " bytes");
			
			/* Update state */
			synchronized(AudioOutputQueue.this) {
				m_lineFramesWritten += bytesWritten / m_bytesPerFrame;
				for(int b=0; b < m_bytesPerFrame; ++b)
					m_lineLastFrame[b] = samples[off + len - (m_bytesPerFrame - b)];
				
				s_logger.finest("Audio output line end is now at " + getEndLineTime() + " after writing " + len / m_bytesPerFrame + " frames");
			}
		}
	}
	
	AudioOutputQueue(final AudioStreamInformationProvider streamInfoProvider) throws LineUnavailableException {
		final AudioFormat audioFormat = streamInfoProvider.getAudioFormat();
		
		/* OSX does not support unsigned PCM lines. We thust always request
		 * a signed line, and convert from unsigned to signed if necessary
		 */
		if (AudioFormat.Encoding.PCM_SIGNED.equals(audioFormat.getEncoding())) {
			m_format = audioFormat;
			m_convertUnsignedToSigned = false;
		}
		else if (AudioFormat.Encoding.PCM_UNSIGNED.equals(audioFormat.getEncoding())) {
			m_format = new AudioFormat(
				audioFormat.getSampleRate(),
				audioFormat.getSampleSizeInBits(),
				audioFormat.getChannels(),
				true,
				audioFormat.isBigEndian()
			);
			m_convertUnsignedToSigned = true;
		}
		else {
			throw new LineUnavailableException("Audio encoding " + audioFormat.getEncoding() + " is not supported");
		}
		
		/* Audio format-dependent stuff */
		m_packetSizeFrames = streamInfoProvider.getFramesPerPacket();
		m_bytesPerFrame = m_format.getChannels() * m_format.getSampleSizeInBits() / 8;
		m_sampleRate = m_format.getSampleRate();
		m_lineLastFrame = new byte[m_bytesPerFrame];
		for(int b=0; b < m_lineLastFrame.length; ++b)
			m_lineLastFrame[b] = (b % 2 == 0) ? (byte)-128 : (byte)0;
			
		/* Compute desired line buffer size and obtain a line */
		final int desiredBufferSize = (int)Math.pow(2, Math.ceil(Math.log(BufferSizeSeconds * m_sampleRate * m_bytesPerFrame) / Math.log(2.0)));
		DataLine.Info lineInfo = new DataLine.Info(
			SourceDataLine.class,
			m_format,
			desiredBufferSize
		);
		m_line = (SourceDataLine)AudioSystem.getLine(lineInfo);
		m_line.open(m_format, desiredBufferSize);
		s_logger.info("Audio output line created and openend. Requested buffer of " + desiredBufferSize / m_bytesPerFrame  + " frames, got " + m_line.getBufferSize() / m_bytesPerFrame + " frames");
		
		/* Start enqueuer thread and wait for the line to start.
		 * The wait guarantees that the AudioClock functions return
		 * sensible values right after construction
		 */
		m_queueThread.setDaemon(true);
		m_queueThread.setName("Audio Enqueuer");
		m_queueThread.setPriority(Thread.MAX_PRIORITY);
		m_queueThread.start();
		while (m_queueThread.isAlive() && !m_line.isActive())
			Thread.yield();
		
		/* Initialize the seconds time offset now that the line is running. */
		m_secondsTimeOffset = 2208988800.0 +  (double)System.currentTimeMillis() * 1e-3;
	}

	/**
	 * Sets the line's MASTER_GAIN control to the provided value,
	 * or complains to the log of the line does not support a MASTER_GAIN control
	 * 
	 * @param gain gain to set
	 */
	private void setLineGain(float gain) {
		if (m_line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
			/* Bound gain value by min and max declared by the control */
			FloatControl gainControl = (FloatControl)m_line.getControl(FloatControl.Type.MASTER_GAIN);
			if (gain < gainControl.getMinimum())
				gainControl.setValue(gainControl.getMinimum());
			else if (gain > gainControl.getMaximum())
				gainControl.setValue(gainControl.getMaximum());
			else
				gainControl.setValue(gain);
		}
		else
			s_logger.severe("Audio output line doesn not support volume control");
	}
	
	private synchronized void applyGain() {
		setLineGain(m_requestedGain);
	}
	
	/**
	 * Sets the desired output gain.
	 * 
	 * @param gain desired gain
	 */
	public synchronized void setGain(float gain) {
		m_requestedGain = gain;
	}
	
	/**
	 * Returns the desired output gain.
	 * 
	 * @param gain desired gain
	 */
	public synchronized float getGain() {
		return m_requestedGain;
	}
	
	/**
	 * Stops audio output 
	 */
	public void close() {
		m_closing = true;
		m_queueThread.interrupt();
	}
	
	/**
	 * Adds sample data to the queue
	 * 
	 * @param playbackRemoteStartFrameTime start time of sample data
	 * @param playbackSamples sample data
	 * @return true if the sample data was added to the queue
	 */
	public synchronized boolean enqueue(long frameTime, byte[] frames) {
		/* Compute playback delay, i.e., the difference between the last sample's
		 * playback time and the current line time
		 */
		double delay =
			(convertFrameToLineTime(frameTime) + frames.length / m_bytesPerFrame - getNowLineTime()) /
			m_sampleRate;
		
		m_latestSeenFrameTime = Math.max(m_latestSeenFrameTime, frameTime);
		
		if (delay < -QueueLengthMaxSeconds / 2.0) {
			/* The whole packet is scheduled to be played in the past */
			s_logger.warning("Audio data arrived " + (-delay) + " seconds too late, dropping");
			return false;
		}
		else if (delay > QueueLengthMaxSeconds / 2.0) {
			/* The packet extends further into the future that our maximum queue size.
			 * We reject it, since this is probably the result of some timing discrepancies
			 */
			s_logger.warning("Audio data arrived " + delay + " seconds too early, dropping");
			return false;
		}
			
		m_queue.put(frameTime, frames);
		return true;
	}
	
	/**
	 * Removes all currently queued sample data
	 */
	public void flush() {
		m_queue.clear();
	}

	@Override
	public synchronized void setFrameTime(long frameTime, double secondsTime) {
		final double ageSeconds = getNowSecondsTime() - secondsTime;
		final long lineTime = Math.round((secondsTime - m_secondsTimeOffset) * m_sampleRate);

		final long frameTimeOffsetPrevious = m_frameTimeOffset;
		m_frameTimeOffset = frameTime - lineTime;
		
		s_logger.fine("Frame time adjusted by " + (m_frameTimeOffset - frameTimeOffsetPrevious) + " based on timing information " + ageSeconds + " seconds old and " + (m_latestSeenFrameTime - frameTime) + " frames before latest seen frame time");
	}

	@Override
	public double getNowSecondsTime() {
		return m_secondsTimeOffset + (double)getNowLineTime() / m_sampleRate;
	}

	@Override
	public long getNowFrameTime() {
		return m_frameTimeOffset + getNowLineTime();
	}

	private synchronized long getEndLineTime() {
		return m_lineFramesWritten;
	}
	
	private long getNowLineTime() {
		return m_line.getLongFramePosition();
	}
	
	private synchronized long convertFrameToLineTime(long entryFrameTime) {
		return entryFrameTime - m_frameTimeOffset;
	}
}
