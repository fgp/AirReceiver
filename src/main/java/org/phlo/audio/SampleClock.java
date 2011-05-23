package org.phlo.audio;

public interface SampleClock {
	public double getNowTime();
	
	public double getNextTime();
	
	public double getSampleRate();
}
