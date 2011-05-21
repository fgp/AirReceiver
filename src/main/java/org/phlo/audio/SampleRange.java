package org.phlo.audio;

public class SampleRange {
	public final SampleOffset offset;
	public final SampleDimensions size;

	public SampleRange(final SampleOffset _offset, final SampleDimensions _size) {
		offset = _offset;
		size = _size;
	}

	public SampleRange(final SampleDimensions _size) {
		offset = SampleOffset.Zero;
		size = _size;
	}
	
	@Override
	public String toString() {
		return
			"[" +
				offset.channel + "..." + (offset.channel + size.channels) + ";" +
				offset.sample + "..." + (offset.sample + size.samples) +
			"]";
	}
}
