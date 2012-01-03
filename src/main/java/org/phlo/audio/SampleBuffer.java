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

package org.phlo.audio;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public final class SampleBuffer implements SampleIndexedAccessor {
	private final SampleDimensions m_bufferDimensions;
	private final float[] m_buffer;
	
	private final SampleIndexer m_samplesIndexer;

	private double m_timeStamp = 0.0;
	
	public SampleBuffer(final float[] buffer, final SampleDimensions bufferDimensions, SampleIndexer samplesIndexer) {
		m_buffer = buffer;
		m_bufferDimensions = bufferDimensions;
		m_samplesIndexer = samplesIndexer;
	}
	
	public SampleBuffer(final float[] buffer, final SampleDimensions bufferDimensions, final SampleRange range, final SampleBufferLayout layout) {
		m_buffer = buffer;
		m_bufferDimensions = bufferDimensions;
		m_samplesIndexer = layout.getIndexer(bufferDimensions, range);
	}

	public SampleBuffer(final SampleDimensions dimensions) {
		this(
			new float[dimensions.getTotalSamples()],
			dimensions,
			new SampleRange(SampleOffset.Zero, dimensions),
			SampleBufferLayout.Banded
		);
	}
	
	public double getTimeStamp() {
		return m_timeStamp;
	}
	
	public void setTimeStamp(double timeStamp) {
		m_timeStamp = timeStamp;
	}
	
	public SampleBuffer slice(SampleRange range) {
		return new SampleBuffer(m_buffer, m_bufferDimensions, m_samplesIndexer.slice(range));
	}

	public SampleBuffer slice(SampleOffset offset, SampleDimensions dimensions) {
		return new SampleBuffer(m_buffer, m_bufferDimensions, m_samplesIndexer.slice(offset, dimensions));
	}

	public void copyFrom(final ByteBuffer src, final SampleDimensions srcDims, final SampleRange srcRange, final SampleByteBufferFormat srcByteFormat) {
		srcDims.assertContains(srcRange);
		m_samplesIndexer.getDimensions().assertContains(srcRange.size);
		
		final SampleIndexedAccessor srcAccessor = srcByteFormat.getAccessor(src, srcDims, srcRange);
		for(int c=0; c < srcRange.size.channels; ++c) {
			for(int s=0; s < srcRange.size.samples; ++s) {
				m_buffer[m_samplesIndexer.getSampleIndex(c, s)] = srcAccessor.getSample(c, s);
			}
		}
	}

	public void copyFrom(final ByteBuffer src, final SampleDimensions srcDims, final SampleByteBufferFormat srcFormat) {
		copyFrom(src, srcDims, new SampleRange(srcDims), srcFormat);
	}
	
	public void copyFrom(final IntBuffer src, final SampleDimensions srcDims, final SampleRange srcRange, final SampleBufferLayout srcLayout, final Signedness srcSignedness) {
		m_samplesIndexer.getDimensions().assertContains(srcRange.size);
		srcDims.assertContains(srcRange);
		
		final SampleIndexer srcIndexer = srcLayout.getIndexer(srcDims, srcRange);
		for(int c=0; c < srcRange.size.channels; ++c) {
			for(int s=0; s < srcRange.size.samples; ++s) {
				m_buffer[m_samplesIndexer.getSampleIndex(c, s)] =
					srcSignedness.shortToNormalizedFloat((short)src.get(srcIndexer.getSampleIndex(c, s)));
			}
		}
	}
	
	public void copyFrom(final IntBuffer src, final SampleDimensions srcDims, final SampleBufferLayout srcLayout, final Signedness srcSignedness) {
		copyFrom(src, srcDims, new SampleRange(srcDims), srcLayout, srcSignedness);
	}

	public void copyTo(final ByteBuffer dst, final SampleDimensions dstDims, final SampleOffset dstOffset, final SampleByteBufferFormat dstByteFormat) {
		dstDims.assertContains(new SampleRange(dstOffset, m_samplesIndexer.getDimensions()));
		
		final SampleIndexedAccessor dstAccessor = dstByteFormat.getAccessor(dst, dstDims, dstOffset);
		for(int c=0; c < m_samplesIndexer.getDimensions().channels; ++c) {
			for(int s=0; s < m_samplesIndexer.getDimensions().samples; ++s) {
				dstAccessor.setSample(c, s, m_buffer[m_samplesIndexer.getSampleIndex(c, s)]);
			}
		}
	}

	public void copyTo(final ByteBuffer dst, final SampleDimensions dstDims, final SampleByteBufferFormat dstFormat) {
		copyTo(dst, dstDims, SampleOffset.Zero, dstFormat);
	}

	@Override
	public SampleDimensions getDimensions() {
		return m_samplesIndexer.getDimensions();
	}

	@Override
	public float getSample(int channel, int sample) {
		return m_buffer[m_samplesIndexer.getSampleIndex(channel, sample)];
	}

	@Override
	public void setSample(int channel, int sample, float value) {
		m_buffer[m_samplesIndexer.getSampleIndex(channel, sample)] = value;
	}
}
