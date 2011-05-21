package org.phlo.audio;

import java.nio.ByteBuffer;

import javax.sound.sampled.SourceDataLine;

public final class SampleBuffer {
	private final SampleDimensions m_dimensions;
	private final long m_frameTime;
	private final float[][] m_samples;

	public SampleBuffer(final SampleDimensions size, final long frameTime) {
		m_dimensions = size;
		m_samples = new float[size.channels][size.samples];
		m_frameTime = frameTime;
	}
	
	public long getFrameTime() {
		return m_frameTime;
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
	
	public SampleRange writeTo(SampleRange srcRange, final SourceDataLine line, final ByteFormat lineByteFormat) {
		m_dimensions.assertContains(srcRange);
		lineByteFormat.layout.assertEquals(SampleLayout.Interleaved);
		
		ByteBuffer buffer = lineByteFormat.allocateBuffer(srcRange.size);
		copyTo(srcRange, buffer, srcRange.size, SampleOffset.Zero, lineByteFormat);
		
		int nextByte = line.write(buffer.array(), buffer.arrayOffset(), buffer.capacity());
		int nextSample = nextByte / (m_dimensions.channels * lineByteFormat.getBytesPerSample());
		assert nextSample <= m_dimensions.samples;
		
		return new SampleRange(
			new SampleOffset(0, nextSample),
			new SampleDimensions(m_dimensions.channels, m_dimensions.samples - nextSample)
		);
	}
}
