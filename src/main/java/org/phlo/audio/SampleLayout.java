package org.phlo.audio;

public enum SampleLayout {
	Interleaved {
		@Override
		public final Indexer getIndexer(final SampleDimensions size, final SampleOffset offset) {
			return new Indexer() {
				@Override public int getSampleIndex(final int channel, final int sample) {
					return (offset.sample + sample) * size.channels + offset.channel + channel;
				}

			};
		}
	},

	Banded {
		@Override
		public final Indexer getIndexer(final SampleDimensions size, final SampleOffset offset) {
			return new Indexer() {
				@Override public int getSampleIndex(final int channel, final int sample) {
					return (offset.channel + channel) * size.samples + offset.sample + sample;
				}

			};
		}
	};

	public interface Indexer {
		int getSampleIndex(int channel, int sample);
	}

	public abstract Indexer getIndexer(SampleDimensions size, SampleOffset offset);

	public final Indexer getIndexer(final SampleDimensions size) {
		return getIndexer(size, SampleOffset.Zero);
	}
	
	public final void assertEquals(SampleLayout layout) {
		if (!equals(layout))
			throw new IllegalArgumentException("Layout " + this + " is not supported");
	}
}
