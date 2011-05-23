package org.phlo.audio;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import javax.sound.sampled.SourceDataLine;

public final class SampleBuffer {
	private final SampleDimensions m_dimensions;
	private final float[][] m_samples;

	private double m_timeStamp = 0.0;

	public SampleBuffer(final SampleDimensions size) {
		m_dimensions = size;
		m_samples = new float[size.channels][size.samples];
	}
	
	public double getTimeStamp() {
		return m_timeStamp;
	}
	
	public void setTimeStamp(double timeStamp) {
		m_timeStamp = timeStamp;
	}
	
	public SampleDimensions getDims() {
		return m_dimensions;
	}

	public float[][] getSamples() {
		return m_samples;
	}

	public void copyFrom(final SampleOffset dstOffset, final ByteBuffer src, final SampleDimensions srcDims, final SampleRange srcRange, final ByteFormat srcByteFormat) {
		srcDims.assertContains(srcRange);
		m_dimensions.assertContains(new SampleRange(dstOffset, srcRange.size));
		
		srcByteFormat.accessSamples(src, srcDims, new Block<Void, ByteFormat.Accessor>() {
			@Override public Void block(ByteFormat.Accessor accessor) {
				for(int c=0; c < srcRange.size.channels; ++c) {
					for(int s=0; s < srcRange.size.samples; ++s) {
						m_samples[dstOffset.channel + c][dstOffset.sample + s] =
							accessor.getSample(srcRange.offset.channel + c, srcRange.offset.sample + s);
					}
				}
				return null;
			}
		});
	}

	public void copyFrom(final ByteBuffer src, final SampleDimensions srcDims, final ByteFormat srcFormat) {
		copyFrom(SampleOffset.Zero, src, srcDims, new SampleRange(srcDims), srcFormat);
	}
	
	public void copyFrom(final SampleOffset dstOffset, final IntBuffer src, final SampleDimensions srcDims, final SampleRange srcRange, final SampleLayout srcLayout, final Signedness srcSignedness) {
		srcDims.assertContains(srcRange);
		m_dimensions.assertContains(new SampleRange(dstOffset, srcRange.size));
		
		SampleLayout.Indexer srcIndexer = srcLayout.getIndexer(srcDims);
		for(int c=0; c < srcRange.size.channels; ++c) {
			for(int s=0; s < srcRange.size.samples; ++s) {
				m_samples[dstOffset.channel + c][dstOffset.sample + s] =
					srcSignedness.shortToNormalizedFloat(
						(short)src.get(srcIndexer.getSampleIndex(
							srcRange.offset.channel + c,
							srcRange.offset.sample + s
						))
					);
			}
		}
	}
	
	public void copyFrom(final IntBuffer src, final SampleDimensions srcDims, final SampleLayout srcLayout, final Signedness srcSignedness) {
		copyFrom(SampleOffset.Zero, src, srcDims, new SampleRange(srcDims), srcLayout, srcSignedness);
	}

	public void copyTo(final SampleRange srcRange, final ByteBuffer dst, final SampleDimensions dstDims, final SampleOffset dstOffset, final ByteFormat dstByteFormat) {
		m_dimensions.assertContains(srcRange);
		dstDims.assertContains(new SampleRange(dstOffset, srcRange.size));
		
		dstByteFormat.accessSamples(dst, dstDims, new Block<Void, ByteFormat.Accessor>() {
			@Override public Void block(ByteFormat.Accessor accessor) {
				for(int c=0; c < srcRange.size.channels; ++c) {
					for(int s=0; s < srcRange.size.samples; ++s) {
						accessor.setSample(
							dstOffset.channel + c, dstOffset.sample + s,
							m_samples[srcRange.offset.channel + c][srcRange.offset.sample + s]);
					}
				}
				return null;
			}
		});
	}

	public void copyTo(final ByteBuffer dst, final SampleDimensions dstDims, final ByteFormat dstFormat) {
		copyTo(new SampleRange(m_dimensions), dst, dstDims, SampleOffset.Zero, dstFormat);
	}
	
	public int writeTo(SampleRange srcRange, final SourceDataLine line, final ByteFormat lineByteFormat) {
		m_dimensions.assertContains(srcRange);
		if (srcRange.size.channels != line.getFormat().getChannels())
			throw new IllegalArgumentException("Line expects " + line.getFormat().getChannels() + " but source range contains " + srcRange.size.channels);
		lineByteFormat.layout.assertEquals(SampleLayout.Interleaved);
		
		ByteBuffer buffer = lineByteFormat.allocateBuffer(srcRange.size);
		copyTo(srcRange, buffer, srcRange.size, SampleOffset.Zero, lineByteFormat);
		
		int writtenBytes = line.write(buffer.array(), buffer.arrayOffset(), buffer.capacity());
		int writtenSamples = writtenBytes / (srcRange.size.channels * lineByteFormat.getBytesPerSample());
		assert writtenSamples <= m_dimensions.samples;
		
		return writtenSamples;
	}
	
	public int writeTo(final SourceDataLine line, final ByteFormat lineByteFormat) {
		return writeTo(new SampleRange(m_dimensions), line, lineByteFormat);
	}
}
