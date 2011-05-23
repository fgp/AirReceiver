package org.phlo.audio;

public final class SampleDimensions {
	public final int channels;
	public final int samples;

	public SampleDimensions(final int _channels, final int _samples) {
		if (_channels < 0)
			throw new IllegalArgumentException("channels must be greater or equal to zero");
		if (_samples < 0)
			throw new IllegalArgumentException("samples must be greater or equal to zero");

		channels = _channels;
		samples = _samples;
	}
	
	public int getTotalSamples() {
		return channels * samples;
	}
	
	public boolean contains(SampleRange range) {
		return
			(range.offset.channel + range.size.channels <= channels) &&
			(range.offset.sample + range.size.samples <= samples);
	}
	
	public boolean contains(SampleOffset offset) {
		return (offset.channel < channels) && (offset.sample < samples);
	}
	
	public boolean contains(SampleDimensions dimensions) {
		return (dimensions.channels < channels) && (dimensions.samples < samples);
	}
	
	public void assertContains(SampleRange range) {
		if (!contains(range))
			throw new IllegalArgumentException("Range " + range + " exceeds dimensions " + this);
	}

	public void assertContains(SampleOffset offset) {
		if (!contains(offset))
			throw new IllegalArgumentException("Offset " + offset + " exceeds dimensions " + this);
	}

	public void assertContains(SampleDimensions dimensions) {
		if (!contains(dimensions))
			throw new IllegalArgumentException("Dimensions " + dimensions + " exceeds dimensions " + this);
	}

	@Override
	public String toString() {
		return "[" + channels + ";" + samples + "]";
	}
}
