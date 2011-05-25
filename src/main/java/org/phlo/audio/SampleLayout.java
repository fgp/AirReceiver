package org.phlo.audio;

public enum SampleLayout {
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

	public abstract SampleIndexer getIndexer(SampleDimensions size, SampleRange range);

	public final SampleIndexer getIndexer(final SampleDimensions dims) {
		return getIndexer(dims, new SampleRange(SampleOffset.Zero, dims));
	}

	public final SampleIndexer getIndexer(final SampleDimensions dims, final SampleOffset offset) {
		dims.assertContains(offset);
		return getIndexer(dims, new SampleRange(offset, dims.reduce(offset.channel, offset.sample)));
	}
}
