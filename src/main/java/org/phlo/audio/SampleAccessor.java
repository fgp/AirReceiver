package org.phlo.audio;

public interface SampleAccessor {
	float getSample(int index);
	void setSample(int index, float sample);
}
