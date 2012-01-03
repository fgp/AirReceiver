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

import javax.sound.sampled.AudioFormat;

/**
 * Described the byte format of individual samples
 * <p>
 * Used as a factory for {SampleAccessor} instances
 * which provide access to the buffer's samples as
 * float values indexed by their absolute position
 * inside the buffer.
 */
public enum SampleByteFormat {
	UnsignedInteger16(2) {
		@Override public final Signedness getSignedness() {
			return Signedness.Unsigned;
		}
		
		@Override public final SampleAccessor getAccessor(final ByteBuffer buffer) {
			return new SampleAccessor() {
				@Override public float getSample(final int index) {
					return Signedness.Unsigned.shortToNormalizedFloat(buffer.getShort(BytesPerSample * index));
				}

				@Override public void setSample(final int index, final float sample) {
					buffer.putShort(BytesPerSample * index, Signedness.Unsigned.shortFromNormalizedFloat(sample));
				}
			};
		}
	},

	SignedInteger16(2) {
		@Override public final Signedness getSignedness() {
			return Signedness.Signed;
		}

		@Override public final SampleAccessor getAccessor(final ByteBuffer buffer) {
			return new SampleAccessor() {
				@Override public float getSample(final int index) {
					return Signedness.Signed.shortToNormalizedFloat(buffer.getShort(BytesPerSample * index));
				}

				@Override public void setSample(final int index, final float sample) {
					buffer.putShort(BytesPerSample * index, Signedness.Signed.shortFromNormalizedFloat(sample));
				}
			};
		}
	},

	Float32(4) {
		@Override public final Signedness getSignedness() {
			return null;
		}

		@Override public final SampleAccessor getAccessor(final ByteBuffer buffer) {
			return new SampleAccessor() {
				@Override public float getSample(final int index) {
					return buffer.getFloat(BytesPerSample * index);
				}

				@Override public void setSample(final int index, final float sample) {
					buffer.putFloat(BytesPerSample * index, sample);
				}
			};
		}
	};
	
	public static SampleByteFormat fromAudioFormat(AudioFormat audioFormat) {
		if (
			(AudioFormat.Encoding.PCM_SIGNED.equals(audioFormat.getEncoding())) &&
			(audioFormat.getSampleSizeInBits() == 16)
		) {
			return SignedInteger16;
		}
		else if (
			(AudioFormat.Encoding.PCM_UNSIGNED.equals(audioFormat.getEncoding())) &&
			(audioFormat.getSampleSizeInBits() == 16)
		) {
			return UnsignedInteger16;
		}
		else {
			throw new IllegalArgumentException("Audio format with encoding " + audioFormat.getEncoding() + " and " + audioFormat.getSampleSizeInBits() + " bits per sample is unsupported");
		}
	}

	public final int BytesPerSample;

	private SampleByteFormat(final int _bytesPerSample) {
		BytesPerSample = _bytesPerSample;
	}
	
	/**
	 * Returns the size required to store a buffer of the
	 * given {@code dimensions}.
	 * 
	 * @param dimensions dimensions of the buffer
	 * @return size of the buffer in bytes
	 */
	public int getSizeBytes(SampleDimensions dimensions) {
		return dimensions.getTotalSamples() * BytesPerSample;
	}
	
	/**
	 * Computes the number of samples in a buffer from
	 * it's size in bytes and the number of channels.
	 * 
	 * @param channels number of channels
	 * @param byteSize size in bytes
	 * @return dimensions of the buffer
	 */
	public SampleDimensions getDimensionsFromChannelsAndByteSize(int channels, int byteSize) {
		return new SampleDimensions(channels, byteSize / (BytesPerSample * channels));
	}
	
	/**
	 * Returns the signedness (signed or unsigned) of
	 * a sample byte format
	 * 
	 * @return signedness, or null of not relevant
	 */
	public abstract Signedness getSignedness();

	/**
	 * Returns an accessor for a given byte buffer
	 * 
	 * @param buffer byte buffer to access
	 * @return accessor instance
	 */
	public abstract SampleAccessor getAccessor(ByteBuffer buffer);
}
