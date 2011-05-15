package org.phlo.AirReceiver;

public interface AudioClock {
	double getNowSecondsTime();
	long getNowFrameTime();
	
	void setFrameTime(long frameTime, double secondsTime);
}
