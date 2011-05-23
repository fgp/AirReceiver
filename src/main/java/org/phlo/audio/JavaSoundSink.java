package org.phlo.audio;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.sampled.*;

public class JavaSoundSink implements SampleClock {
	private static Logger s_logger = Logger.getLogger(JavaSoundSink.class.getName());

	private static final double TimeOffset = 2208988800.0;
	
	private static final int BytesPerSample = 2;
	
	private static final double BufferSizeSeconds = 0.2;
	
	private final double m_sampleRate;

	private final int m_channels;

	private final SampleSource m_sampleSource;
	
	private final AudioFormat m_javaSoundAudioFormat;
	
	private final SourceDataLine m_javaSoundLine;
	
	private final FloatControl m_javaSoundLineControlMasterGain;
	
	private final float m_javaSoundLineControlMasterGainMin;

	private final float m_javaSoundLineControlMasterGainMax;

	private final JavaSoundLineWriter m_javaSoundLineWriter;
	
	private final double m_lineStartTime;
	
	private volatile double m_startTime;
	
	private float m_currentGain;
	
	private float m_requestedGain;
	
	private final class JavaSoundLineWriter extends Thread {		
		/**
		 * Latch used to pass the line start time to the outer class'es constructor.
		 */
		final Latch<Double> lineStartTimeLatch = new Latch<Double>(JavaSoundSink.this);
		
		/**
		 * Latch used to signal that the outer class'es constructor has set
		 * the line start time. Uses the outer instance as monitor to make sure
		 * that we actually see the updated instance variable.
		 * 
		 */
		final Latch<Void> startTimeSetLatch = new Latch<Void>(JavaSoundSink.this);
		
		/**
		 * Latch used to signal that the inner class'es run() method has initialized
		 * the line end time. Uses the inner instance as monitor to make sure that
		 * we actually see the updated instance variable.
		 */
		final Latch<Void> endTimeSetLatch = new Latch<Void>(JavaSoundSink.this);

		public volatile boolean exit = false;

		private double m_lineEndTime;
		
		@Override
		public void run() {
			try {
				setPriority(MAX_PRIORITY);
				
				/* Arrange for the current time to be offered via the
				 * lineStartTimeLatch one the line actually starts.
				 * The outer class'es constructor waits for this, sets up
				 * the m_lineStartTime instance variable, and offers
				 * on the startTimeSetLatch afterwards.
				 */
				{
					m_javaSoundLine.addLineListener(new LineListener() {
						@Override public void update(LineEvent evt) {
							if (LineEvent.Type.START.equals(evt.getType())) {
								/* Offer the current system time */
								try {
										lineStartTimeLatch.offer(TimeOffset + 1e-3 * (double)System.currentTimeMillis());
								}
								catch (InterruptedException e) {
									s_logger.log(Level.WARNING, "Java Sound line writer was interrupted during startup", e);
									throw new RuntimeException(e.getMessage(), e);
								}
								
								/* We're not interested in further notifications */
								m_javaSoundLine.removeLineListener(this);
							}
						}
					});
				}
				
				/* Get the line's byte format and allocate a buffer containing silence */
				ByteFormat lineByteFormat = new ByteFormat(m_javaSoundAudioFormat);
				SampleBuffer silenceBuffer = new SampleBuffer(
					lineByteFormat.getDimensionsFromChannelsAndByteSize(
						m_channels,
						m_javaSoundLine.getBufferSize()
					)
				);
				
				/* Start the line. It probably won't actuall start until we write some data */
				m_javaSoundLine.start();
				
				/* Write some silence to the line. This is supposed to start the line.
				 * Note that we *must* *not* call advanceEndTime here, since the end
				 * time hasn't yet been initialized. Instead, we remember the number of
				 * sample we wrote and call advanceEndTime() after the outer class
				 * signals us that lineStartTime has been set
				 */
				{
					final int silenceWritten = silenceBuffer.writeTo(m_javaSoundLine, lineByteFormat);
	
					/* Wait for the outer class'es constructor to initialize m_lineStartTime
					 * and use that to initialize the line end time
					 */
					startTimeSetLatch.consume();
					synchronized(this) {
						m_lineEndTime = m_lineStartTime;
						advanceEndTime(silenceWritten);
					}
					endTimeSetLatch.offer(null);
				}
	
				/* We're now up and running */
				
				boolean lineIsMuted = false;
				boolean lineIsPaused = true;
				while (!exit) {
					/* Check whether the line's supposed to be paused
					 * If it is, we mute the line, otherwise we un-mute
					 */
					final boolean lineShouldPause = m_startTime > m_lineEndTime;
					if (lineIsPaused != lineShouldPause) {
						if (lineShouldPause) {
							mute();
							lineIsPaused = true;
							s_logger.log(Level.INFO, "Audio output paused at " + m_lineEndTime);
						}
						else {
							unmute();
							lineIsPaused = false;
							s_logger.log(Level.INFO, "Audio output resumed at " + m_lineEndTime);
						}
					}
					
					/* Get a buffer to write from the sample source. If it
					 * doesn't provide one, we mute the line, otherwise we un-mute,
					 * similar to the paused handling above. In fact, these states
					 * are only kept separately for the sake of the log messages
					 */
					final SampleBuffer sampleSourceBuffer;
					if (lineIsPaused) {
						sampleSourceBuffer = null;
					}
					else {
						sampleSourceBuffer = m_sampleSource.getSampleBuffer(m_lineEndTime);
						
						/* If the source provides no data, we mute the line.
						 * Once it resumed, we unmute again
						 */
						if ((sampleSourceBuffer == null) != lineIsMuted) {
							if (sampleSourceBuffer != null) {
								unmute();
								lineIsMuted = false;
								s_logger.log(Level.WARNING, "Audio sample source resumed providing samples at " + m_lineEndTime + ", un-muted line");
							}
							else {
								mute();
								lineIsMuted = true;
								s_logger.log(Level.WARNING, "Audio sample source stopped providing samples at " + m_lineEndTime + ", muted line");
							}
						}
					}
					
					/* Compute the number of silence samples to insert before
					 * the sample source buffer (in case of a gap, pause or
					 * under-run) and the number of samples to skip from that
					 * buffer (in case of an overlap)
					 */
					final int silenceSamples;
					final int skipSamples;
					if (sampleSourceBuffer != null) {
						silenceSamples = (int)Math.round(Math.min(Math.max(
							0.0,
							(sampleSourceBuffer.getTimeStamp() - m_lineEndTime) * m_sampleRate),
							(double)silenceBuffer.getDims().samples
						));
						skipSamples = (int)Math.round(Math.min(Math.max(
							0.0,
							(m_lineEndTime - sampleSourceBuffer.getTimeStamp()) * m_sampleRate),
							(double)sampleSourceBuffer.getDims().samples
						));
					}
					else {
						silenceSamples = (int)Math.ceil(Math.min(
							(m_startTime - m_lineEndTime) * m_sampleRate,
							(double)silenceBuffer.getDims().samples
						));
						skipSamples = -1;
					}
					
					
					/* Write silence samples */
					if (silenceSamples > 0) {
						advanceEndTime(
							silenceBuffer.writeTo(
								new SampleRange(
									SampleOffset.Zero,
									new SampleDimensions(
										m_channels,
										silenceSamples
								)),
								m_javaSoundLine,
								lineByteFormat
							)
						);
					}
					
					/* Write sample source samples */
					if (sampleSourceBuffer != null) {
						if (skipSamples >= sampleSourceBuffer.getDims().samples) {
							s_logger.warning("Audio output overlaps " + skipSamples + " samples, ignored buffer");
						}
						else if (silenceSamples >= sampleSourceBuffer.getDims().samples) {
							s_logger.warning("Audio output has gap of " + skipSamples + " samples, ignored buffer");
						}
						else {
							if (skipSamples > 0)
								s_logger.warning("Audio output overlaps " + skipSamples + " samples, skipped samples");
							if (silenceSamples > 0)
								s_logger.warning("Audio output has gap of " + skipSamples + " samples, filled with silence");
							
							advanceEndTime(
								sampleSourceBuffer.writeTo(
									new SampleRange(
										new SampleOffset(
											0,
											skipSamples
										),
										new SampleDimensions(
											Math.min(m_channels, sampleSourceBuffer.getDims().channels),
											sampleSourceBuffer.getDims().samples - skipSamples
										)
									),
									m_javaSoundLine,
									lineByteFormat
								)
							);
						}
					}
				} /* while (!exit)
				
				/* Exiting */
				
				/* Write some silence and mute the line to prevent
				 * it from emitting noise while it's closing
				 */
				silenceBuffer.writeTo(m_javaSoundLine, lineByteFormat);
				mute();
				
				m_javaSoundLine.stop();
			}
			catch (InterruptedException e) {
				/* Done */
			}
		}
		
		private synchronized void advanceEndTime(int samples) {
			m_lineEndTime += (double)samples / m_sampleRate;
		}
		
		public synchronized double getEndTime() {
			return m_lineEndTime;
		}
	}
	
	public JavaSoundSink(final double sampleRate, int channels, SampleSource sampleSource)
		throws InterruptedException
	{
		/* Initialize instance variables */
		
		m_sampleRate = sampleRate;
		m_channels = channels;
		m_sampleSource = sampleSource;
		m_startTime = Double.MAX_VALUE;

		/* Create and open JavaSound SourceDataLine */
		
		final int bufferSizeBytes = (int)Math.round(BufferSizeSeconds * m_sampleRate * (double)channels * (double)BytesPerSample);
		m_javaSoundAudioFormat = new AudioFormat(
			(float)m_sampleRate,
			BytesPerSample * 8,
			channels,
			true,
			true
		);
		final DataLine.Info lineInfo = new DataLine.Info(
			SourceDataLine.class,
			m_javaSoundAudioFormat,
			bufferSizeBytes
		);
		try {
			m_javaSoundLine = (SourceDataLine)AudioSystem.getLine(lineInfo);
			m_javaSoundLine.open(m_javaSoundAudioFormat, bufferSizeBytes);
		} catch (LineUnavailableException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
		
		/* Get Master Gain Control for Java Sound SourceDataLine */
		
		if (m_javaSoundLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
			m_javaSoundLineControlMasterGain = (FloatControl)m_javaSoundLine.getControl(FloatControl.Type.MASTER_GAIN);
			m_javaSoundLineControlMasterGainMin = m_javaSoundLineControlMasterGain.getMinimum();
			m_javaSoundLineControlMasterGainMax = m_javaSoundLineControlMasterGain.getMaximum();
		}
		else {
			m_javaSoundLineControlMasterGain = null;
			m_javaSoundLineControlMasterGainMin = 0.0f;
			m_javaSoundLineControlMasterGainMax = 0.0f;
		}
		
		/* Start the line writer thread.
		 * The line writer starts the line and passes the system time it started at
		 * via the lineStartTimeLatch. We then initialize the corresponding instance
		 * variable, signal the line writer thread, and finally wait for it to 
		 * initialize it's the line end time instance variables.
		 */
		
		m_javaSoundLineWriter = new JavaSoundLineWriter();
		m_javaSoundLineWriter.start();
		m_lineStartTime = m_javaSoundLineWriter.lineStartTimeLatch.consume();
		m_javaSoundLineWriter.startTimeSetLatch.offer(null);
		m_javaSoundLineWriter.endTimeSetLatch.consume();
	}
	
	public void close() {
		/* Tell the writer to exit and wait for that to happen */
		m_javaSoundLineWriter.exit = true;
		while (m_javaSoundLineWriter.isAlive()) {
			try {
				/* Be patient ... */
				Thread.sleep(1);
			}
			catch (InterruptedException e) {
				/* But when stressed, share the trouble ... */
				m_javaSoundLineWriter.interrupt();
			}
		}

		m_javaSoundLine.close();
	}
	
	public void setStartTime(double timeStamp) {
		m_startTime = timeStamp;
	}

	public synchronized void setGain(float gain) {
		m_requestedGain = gain;
		if (m_requestedGain != m_currentGain)
			setJavaSoundLineGain(m_requestedGain);
	}
	
	public synchronized float getGain(float gain) {
		return m_requestedGain;
	}

	@Override
	public double getNowTime() {
		return m_lineStartTime + (double)m_javaSoundLine.getLongFramePosition() / m_sampleRate;
	}

	@Override
	public double getNextTime() {
		return m_javaSoundLineWriter.getEndTime();
	}

	@Override
	public double getSampleRate() {
		return m_sampleRate;
	}
	
	private synchronized void setJavaSoundLineGain(float gain) {
		m_javaSoundLineControlMasterGain.setValue(Math.max(Math.min(
			m_javaSoundLineControlMasterGainMin,
			gain),
			m_javaSoundLineControlMasterGainMax
		));
		m_currentGain = gain;
	}
	
	private synchronized void mute() {
		if (m_currentGain > Float.MIN_VALUE)
			setJavaSoundLineGain(Float.MIN_VALUE);
	}
	
	private synchronized void unmute() {
		if (m_currentGain == Float.MIN_VALUE)
			setJavaSoundLineGain(m_requestedGain);
	}	
}
