package org.phlo.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.sound.sampled.*;

public final class ByteFormat {
	public final SampleLayout layout;
	public final ByteOrder byteOrder;
	public final SampleFormat sampleFormat;
	
	public ByteFormat(SampleLayout _layout, ByteOrder _byteOrder, SampleFormat _sampleFormat) {
		layout = _layout;
		byteOrder = _byteOrder;
		sampleFormat = _sampleFormat;
	}
	
	public ByteFormat(AudioFormat audioFormat) {
		this(
			SampleLayout.Interleaved,
			audioFormat.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN,
			SampleFormat.fromAudioFormat(audioFormat)
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
