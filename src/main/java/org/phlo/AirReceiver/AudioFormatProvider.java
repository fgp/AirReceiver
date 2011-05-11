package org.phlo.AirReceiver;

import javax.sound.sampled.AudioFormat;

public interface AudioFormatProvider {
	public AudioFormat getAudioFormat();
	public int getFramesPerPacket();
}
