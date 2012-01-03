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

/**
 * Describes the layout of the samples in a buffer.
 * <p>
 * Serves as a factory for {@link SampleIndexer} instances
 * which provide methods to convert separate channel and
 * sample indices into a combined sample index.
 */
public enum SampleBufferLayout {
	Interleaved {
		@Override
		public final SampleIndexer getIndexer(final SampleDimensions bufferDimensions, final SampleRange indexedRange) {
			return new SampleIndexer() {
				@Override public int getSampleIndex(final int channel, final int sample) {
					return (indexedRange.offset.sample + sample) * bufferDimensions.channels + indexedRange.offset.channel + channel;
				}

				@Override
				public SampleDimensions getDimensions() {
					return indexedRange.size;
				}

				@Override
				public SampleIndexer slice(SampleOffset offset, SampleDimensions dimensions) {
					return getIndexer(bufferDimensions, indexedRange.slice(offset, dimensions));
				}

				@Override
				public SampleIndexer slice(SampleRange range) {
					return getIndexer(bufferDimensions, indexedRange.slice(range));
				}
			};
		}
	},

	Banded {
		@Override
		public final SampleIndexer getIndexer(final SampleDimensions bufferDimensions, final SampleRange indexedRange) {
			return new SampleIndexer() {
				@Override public int getSampleIndex(final int channel, final int sample) {
					return (indexedRange.offset.channel + channel) * bufferDimensions.samples + indexedRange.offset.sample + sample;
				}

				@Override
				public SampleDimensions getDimensions() {
					return indexedRange.size;
				}

				@Override
				public SampleIndexer slice(SampleOffset offset, SampleDimensions dimensions) {
					return getIndexer(bufferDimensions, indexedRange.slice(offset, dimensions));
				}

				@Override
				public SampleIndexer slice(SampleRange range) {
					return getIndexer(bufferDimensions, indexedRange.slice(range));
				}
			};
		}
	};

	/**
	 * Returns a {@link SampleIndexer} which indices the sample in the
	 * given {@code indexedRange} inside a buffer with the given
	 * {@code bufferDimensions}.
	 * 
	 * @param bufferDimensions The buffer's dimensions
	 * @param indexedRange The range to index
	 * @return Instance of {@link SampleIndexer}
	 */
	public abstract SampleIndexer getIndexer(SampleDimensions bufferDimensions, SampleRange indexedRange);

	/**
	 * Returns a {@link SampleIndexer} which indices the sample inside a
	 * buffer with the given {@code bufferDimensions}.
	 * 
	 * @param bufferDimensions The buffer's dimensions
	 * @return Instance of {@link SampleIndexer}
	 */
	public final SampleIndexer getIndexer(final SampleDimensions dims) {
		return getIndexer(dims, new SampleRange(SampleOffset.Zero, dims));
	}

	/**
	 * Returns a {@link SampleIndexer} which indices the sample inside a
	 * buffer with the given {@code bufferDimensions} starting at
	 * {@code offset}
	 * 
	 * @param bufferDimensions The buffer's dimensions
	 * @param offset The offset at which the indices start
	 * @return Instance of {@link SampleIndexer}
	 */
	public final SampleIndexer getIndexer(final SampleDimensions dims, final SampleOffset offset) {
		dims.assertContains(offset);
		return getIndexer(dims, new SampleRange(offset, dims.reduce(offset.channel, offset.sample)));
	}
}
