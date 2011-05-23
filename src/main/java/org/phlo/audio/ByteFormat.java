package org.phlo.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.sound.sampled.*;

public final class ByteFormat {
	public interface Accessor {
		float getSample(int channel, int sample);
		void setSample(int channel, int index, float sample);
	}
	
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
	
	public int getBytesPerSample() {
		return sampleFormat.BytesPerSample;
	}
	
	public int getSizeBytes(SampleDimensions dimensions) {
		return dimensions.getTotalSamples() * getBytesPerSample();
	}
	
	public SampleDimensions getDimensionsFromChannelsAndByteSize(int channels, int byteSize) {
		return new SampleDimensions(channels, byteSize / (getBytesPerSample() * channels));
	}
	
	public ByteBuffer allocateBuffer(SampleDimensions dimensions) {
		ByteBuffer buffer = ByteBuffer.allocate(getSizeBytes(dimensions));
		buffer.order(byteOrder);
		return buffer;
	}
	
	public ByteBuffer wrapBytes(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		buffer.order(byteOrder);
		return buffer;
	}

	public <R> R accessSamples(ByteBuffer buffer, SampleDimensions dimensions, final Block<R,Accessor> block) {
		final SampleLayout.Indexer sampleIndexer = layout.getIndexer(dimensions);
		final SampleFormat.Accessor sampleAccessor = sampleFormat.getAccessor(buffer);
		
		ByteOrder orderOriginal = buffer.order();
		try {
			buffer.order(byteOrder);

			return block.block(new Accessor() {
				@Override
				public float getSample(int channel, int sample) {
					return sampleAccessor.getSample(sampleIndexer.getSampleIndex(channel, sample));
				}

				@Override
				public void setSample(int channel, int sample, float value) {
					sampleAccessor.setSample(sampleIndexer.getSampleIndex(channel, sample), value);
				}
			});
		}
		finally {
			buffer.order(orderOriginal);
		}
	}
}
