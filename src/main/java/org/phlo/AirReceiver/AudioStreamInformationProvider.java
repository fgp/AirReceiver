package org.phlo.AirReceiver;

import javax.sound.sampled.AudioFormat;

public interface AudioStreamInformationProvider {
	public AudioFormat getAudioFormat();
	public int getFramesPerPacket();
	public double getPacketsPerSecond();
}
