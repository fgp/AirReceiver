package org.phlo.AirReceiver;

public interface AudioClock {
	double getLocalSecondsOffset();

	long getNowLocalFrameTime();
	double getNowLocalSecondsTime();
	
	void requestSyncRemoteFrameTime(long remoteFrameTime, double localSecondsTime, boolean force);
}
