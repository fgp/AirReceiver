package org.phlo.audio;

public class SampleOffset {
	public final int channel;
	public final int sample;

	public final static SampleOffset Zero = new SampleOffset(0, 0);

	public SampleOffset(final int _channel, final int _sample) {
		if (_channel < 0)
			throw new IllegalArgumentException("channel must be greater or equal to zero");
		if (_sample < 0)
			throw new IllegalArgumentException("sample must be greater or equal to zero");

		channel = _channel;
		sample = _sample;
	}
	
	@Override
	public String toString() {
		return "[" + channel + ";" + sample + "]";
	}
}
