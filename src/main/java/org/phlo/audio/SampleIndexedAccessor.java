package org.phlo.audio;

public interface SampleIndexedAccessor {
	SampleDimensions getDimensions();
	
	SampleIndexedAccessor slice(SampleOffset offset, SampleDimensions dimensions);

	SampleIndexedAccessor slice(SampleRange range);

	float getSample(int channel, int sample);
	void setSample(int channel, int index, float sample);
}
