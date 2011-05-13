package org.phlo.AirReceiver;

import java.util.Arrays;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.sampled.*;

public class AudioOutputQueue implements AudioClock {
	private static Logger s_logger = Logger.getLogger(AudioOutputQueue.class.getName());

	private static final double QueueLengthMaxSeconds = 5;
	private static final double BufferSizeSeconds = 0.05;
	private static final double TimingPrecision = 0.1;

	/**
	 * Signals that the queue is being closed.
	 * Never transitions from true to false!
	 */
	private volatile boolean m_closing = false;
	
	private final double m_localSecondsOffset = (double)0x83aa7e80L;

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
	 * Active remote frame time offset
	 */
	private long m_activeRemoteFrameTimeOffset = 0;
	
	/**
	 * Requested remote frame time offset
	 */
	private long m_requestedRemoteFrameTimeOffset = 0;
	
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

				/* Start the line */
				m_line.start();
				
				boolean playing = false;
				while (!m_closing) {
					final long writableFrames = Math.min(m_line.getBufferSize(), m_line.available()) / m_bytesPerFrame;
					
					/* If the gap between the next packet and the end of line is
					 * negligible (fits into the line buffer), we write it to the line.
					 * Otherwise, we fill the line buffer with silence and hope for
					 * further packets to appear in the queue
					 */
					if (!m_queue.isEmpty()) {
						/* Get earliest packet from queue and compute playback time and gap */
						final long entryRemoteTime = m_queue.firstKey();
						final long entryFrameTime = fromRemoteFrameTime(entryRemoteTime);
						final long gapFrames = entryFrameTime - getEndLocalFrameTime();
						
						if (gapFrames <= writableFrames) {
							if (!playing) {
								playing = true;
								setLineGain(m_requestedGain);
								s_logger.fine("Audio playback resumed, un-muted line");
							}
							
							/* Negligible gap between packet and line end. Prepare packet for playback */
							final byte[] nextPlaybackSamples = m_queue.remove(entryRemoteTime);
							int nextPlaybackSamplesLength = nextPlaybackSamples.length;
							if (nextPlaybackSamplesLength % m_bytesPerFrame != 0) {
								s_logger.severe("Audio data contains non-integral number of frames, ignore last " + (nextPlaybackSamplesLength % m_bytesPerFrame) + " bytes");
								nextPlaybackSamplesLength -= nextPlaybackSamplesLength % m_bytesPerFrame;
							}
							
							/* Append packet to line */
							s_logger.finest("Audio data containing " + nextPlaybackSamplesLength / m_bytesPerFrame + " frames for playback time " + entryFrameTime + " found in queue, appending to the output line");
							appendFrames(nextPlaybackSamples, 0, nextPlaybackSamplesLength, entryFrameTime, false);
							continue;
						}
					}
					
					if (playing) {
						playing = false;
						setLineGain(Float.NEGATIVE_INFINITY);
						s_logger.warning("Audio data missing starting with frame time " + getEndLocalFrameTime() + ", writing " + writableFrames + " frames of silence and muted line");
					}
					
					appendFrames(null, 0, 0, getEndLocalFrameTime() + writableFrames, false);
					s_logger.finest("Audio data missing for frame time " + getEndLocalFrameTime() + ", wrote " + writableFrames + " frames of silence");
				}
				
				/* Before we exit, we fill the line's buffer with silence. This should prevent
				 * noise from being output while the line is being stopped
				 */
				appendFrames(null, 0, 0, getEndLocalFrameTime() + m_line.available() / m_bytesPerFrame, true);
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
		public void appendFrames(byte[] samples, int off, int len, long time, boolean warnNonContinous) {
			assert off % m_bytesPerFrame == 0;
			assert len % m_bytesPerFrame == 0;

			while (true) {
				/* Fetch line end time only once per iteration */
				long endFrameTime = getEndLocalFrameTime();
				
				if (endFrameTime == time) {
					/* Samples to append scheduled exactly at line end. Just append them and be done */

					appendFrames(samples, off, len);
					break;
				}
				else if (endFrameTime < time) {
					/* Samples to append scheduled after the line end. Fill the gap with silence */

					if (warnNonContinous)
						s_logger.warning("Audio output non-continous (end of line is " + endFrameTime + " but requested playback time is  " + time + "), writing " + (time - endFrameTime) + " frames of silence");

					byte[] silenceFrames = new byte[(int)(time - endFrameTime) * m_bytesPerFrame];
					for(int i = 0; i < silenceFrames.length; ++i)
						silenceFrames[i] = m_lineLastFrame[i % m_bytesPerFrame];
					appendFrames(silenceFrames, 0, silenceFrames.length);
				}
				else if (endFrameTime > time) {
					/* Samples to append scheduled before the line end. Remove the overlapping
					 * part and retry
					 */
					
					if (warnNonContinous)
						s_logger.warning("Audio output non-continous (end of line is " + endFrameTime + " but requested playback time is  " + time + "), skipping " + (endFrameTime - time) + " frames");

					off += (endFrameTime - time) * m_bytesPerFrame;
					time += endFrameTime - time;
				}
				else {
					/* Strange universe... */
					assert false;
				}
			}
		}
		
		/**
		 * Append the range [off,off+len) from the provided sample data to the line.
		 * 
		 * @param samples sample data
		 * @param off sample data offset
		 * @param len sample data length
		 */
		public void appendFrames(byte[] samples, int off, int len) {
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
				
				s_logger.finest("Audio output line end is now at " + getEndLocalFrameTime() + " after writing " + len / m_bytesPerFrame + " frames");
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
		
		/* Start enqueuer thread and wait for it to start the line */
		m_queueThread.setDaemon(true);
		m_queueThread.setName("Audio Enqueuer");
		m_queueThread.setPriority(Thread.MAX_PRIORITY);
		m_queueThread.start();
		while (!m_line.isActive()) {
			try { Thread.sleep(10); }
			catch (InterruptedException e) { /* Ignore */ }
		}
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
	
	/**
	 * Sets the desired output gain.
	 * 
	 * @param gain desired gain
	 */
	public void setGain(float gain) {
		m_requestedGain = gain;
	}
	
	/**
	 * Returns the desired output gain.
	 * 
	 * @param gain desired gain
	 */
	public float getGain() {
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
	public boolean enqueue(long playbackRemoteStartFrameTime, byte[] playbackSamples) {
		/* Compute playback delay, i.e., the difference between the last sample's
		 * playback time and the current line time
		 */
		long playbackStartFrameTime = fromRemoteFrameTime(playbackRemoteStartFrameTime);
		long playbackDelayFrames = playbackStartFrameTime + playbackSamples.length / m_bytesPerFrame - getNowLocalFrameTime();
		
		if (playbackDelayFrames < 0) {
			/* The whole packet is scheduled to be played in the past */
			s_logger.warning("Audio data arrived " + (-playbackDelayFrames) + " frames too late, dropping");
			return false;
		}
		else if (playbackDelayFrames > QueueLengthMaxSeconds * m_sampleRate) {
			/* The packet extends further into the future that our maximum queue size.
			 * We reject it, since this is probably the result of some timing discrepancies
			 */
			s_logger.warning("Audio data arrived " + (playbackDelayFrames) + " frames too early, dropping");
			return false;
		}
			
		m_queue.put(playbackRemoteStartFrameTime, playbackSamples);
		return true;
	}
	
	/**
	 * Removes all currently queued sample data
	 */
	public void flush() {
		m_queue.clear();
	}
	
	/**
	 * Convert from remote frame time to line frame time
	 * 
	 * @param remoteFrameTime  a remote frame time
	 * @return remoteFrameTime converted to line frame time
	 */
	private synchronized long fromRemoteFrameTime(long remoteFrameTime) {
		return remoteFrameTime - m_activeRemoteFrameTimeOffset;
	}
	
	@Override
	public synchronized void requestSyncRemoteFrameTime(long remoteFrameTime, double localSecondsTime, boolean force) {
		/* Convert local seconds time to frame time */
		final long localFrameTime = Math.round((localSecondsTime - getLocalSecondsOffset()) * (double)m_sampleRate);

		/* Compute the requested offset and the adjustment we'd have to make to the
		 * active offset
		 */
		m_requestedRemoteFrameTimeOffset = remoteFrameTime - localFrameTime;
		double requestedAdjustmentSeconds = (double)(m_requestedRemoteFrameTimeOffset - m_activeRemoteFrameTimeOffset) / m_sampleRate;
		
		if ((Math.abs(requestedAdjustmentSeconds) > TimingPrecision) || force) {
			/* We've either been forced to adjust the offset, or the timing is way off.
			 * We've got no other chance than to adjust the active offset, even if this
			 * probably produces an audible distortion
			 */
			m_activeRemoteFrameTimeOffset = m_requestedRemoteFrameTimeOffset;
			s_logger.warning("Remote frame time to local frame time offset is now " + m_activeRemoteFrameTimeOffset + " after adjustment by " + requestedAdjustmentSeconds + " seconds");
		}
		else {
			/* We're within parameters. Since adjusting the offset produces an
			 * audible distortion, we ignore the sync request
			 */
			s_logger.fine("Remote frame time to local frame time offset is still " + m_activeRemoteFrameTimeOffset + ", requested adjustment was only " + requestedAdjustmentSeconds + " seconds");
		}
	}
	
	/**
	 * Returns the offset of the local seconds time,
	 * i.e. the local seconds time that corresponds
	 * to the local frame time zero.
	 */
	@Override
	public double getLocalSecondsOffset() {
		return m_localSecondsOffset;
	}

	/**
	 * Returns the current line frame time.
	 * 
	 * @return the current line frame time
	 */
	public synchronized long getNowLocalFrameTime() {
		return m_line.getLongFramePosition();
	}
	
	/**
	 * Returns the current line frame time in seconds.
	 * Offsetted by the local seconds epoch to prevent
	 * to make this monotonic even if the queue is
	 * destroyed and re-created.
	 */
	@Override
	public double getNowLocalSecondsTime() {
		return getLocalSecondsOffset() + (double)getNowLocalFrameTime() / m_sampleRate;
	}
	
	/**
	 * Returns the frame time of the current line end,
	 * i.e. the playback time of the last frame written
	 * to the line
	 * 
	 * @return line end frame time
	 */
	private synchronized long getEndLocalFrameTime() {
		return m_lineFramesWritten;
	}
}
