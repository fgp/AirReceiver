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
import java.nio.ByteOrder;

import javax.sound.sampled.*;

/**
 * Described to format of a byte-based sample buffer
 * (usually a {@link ByteBuffer}).
 * <p>
 * Used as a factory for {SampleIndexedAccessor} instances
 * which provide access to the buffer's samples as
 * float values indexed by their channel and sample
 * index.
 */
public final class SampleByteBufferFormat {
	/**
	 * The buffer's layout
	 */
	public final SampleBufferLayout layout;
	
	/**
	 * The buffer's byte order
	 */
	public final ByteOrder byteOrder;
	
	/**
	 * The individual sample's format
	 */
	public final SampleByteFormat sampleFormat;
	
	public SampleByteBufferFormat(SampleBufferLayout _layout, ByteOrder _byteOrder, SampleByteFormat _sampleFormat) {
		layout = _layout;
		byteOrder = _byteOrder;
		sampleFormat = _sampleFormat;
	}
	
	public SampleByteBufferFormat(AudioFormat audioFormat) {
		this(
			SampleBufferLayout.Interleaved,
			audioFormat.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN,
			SampleByteFormat.fromAudioFormat(audioFormat)
		);
	}
	
	public ByteBuffer allocateBuffer(SampleDimensions dimensions) {
		ByteBuffer buffer = ByteBuffer.allocate(sampleFormat.getSizeBytes((dimensions)));
		buffer.order(byteOrder);
		return buffer;
	}
	
	public ByteBuffer wrapBytes(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		buffer.order(byteOrder);
		return buffer;
	}
	
	public SampleIndexedAccessor getAccessor(final ByteBuffer buffer, final SampleDimensions bufferDimensions, final SampleRange range) {
		final ByteBuffer bufferWithByteOrder = buffer.duplicate();
		bufferWithByteOrder.order(byteOrder);
		final SampleIndexer sampleIndexer = layout.getIndexer(bufferDimensions, range);
		final SampleAccessor sampleAccessor = sampleFormat.getAccessor(bufferWithByteOrder);

		return new SampleIndexedAccessor() {
			@Override
			public float getSample(int channel, int sample) {
				return sampleAccessor.getSample(sampleIndexer.getSampleIndex(channel, sample));
			}

			@Override
			public void setSample(int channel, int sample, float value) {
				sampleAccessor.setSample(sampleIndexer.getSampleIndex(channel, sample), value);
			}

			@Override
			public SampleDimensions getDimensions() {
				return range.size;
			}

			@Override
			public SampleIndexedAccessor slice(SampleOffset offset, SampleDimensions dimensions) {
				return getAccessor(buffer, bufferDimensions, range.slice(offset, dimensions));
			}

			@Override
			public SampleIndexedAccessor slice(SampleRange range) {
				return getAccessor(buffer, bufferDimensions, range.slice(range));
			}
		};
	}
	
	public SampleIndexedAccessor getAccessor(final ByteBuffer buffer, final SampleDimensions bufferDimensions, final SampleOffset offset) {
		return getAccessor(buffer, bufferDimensions, new SampleRange(offset, bufferDimensions.reduce(offset.channel, offset.sample)));
	}
	
	public SampleIndexedAccessor getAccessor(final ByteBuffer buffer, final SampleDimensions bufferDimensions) {
		return getAccessor(buffer, bufferDimensions, new SampleRange(SampleOffset.Zero, bufferDimensions));
	}
}
