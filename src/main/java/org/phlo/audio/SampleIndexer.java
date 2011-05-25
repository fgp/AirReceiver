package org.phlo.audio;

public interface SampleIndexer {
	SampleDimensions getDimensions();
	
	SampleIndexer slice(SampleOffset offset, SampleDimensions dimensions);

	SampleIndexer slice(SampleRange range);

	int getSampleIndex(int channel, int sample);
}
