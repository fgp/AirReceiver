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
	
	public SampleRange slice(SampleOffset _offset, SampleDimensions _dimensions) {
		if (_offset == null)
			_offset = SampleOffset.Zero;
		
		if (_dimensions == null)
			_dimensions = size.reduce(_offset.channel, _offset.sample);

		size.assertContains(_offset, _dimensions);
		return new SampleRange(offset.add(_offset), _dimensions);
		
	}
	
	public SampleRange slice(SampleRange _range) {
		return slice(_range.offset, _range.size);
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
