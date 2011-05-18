/*
 * This file is part of AirReceiver.
 *
 * AirReceiver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * AirReceiver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with AirReceiver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.phlo.AirReceiver;

/**
 * Audio device clock
 */
public interface AudioClock {
	/**
	 * Returns the current playback time in seconds.
	 * 
	 * @return time of currently played sample
	 */
	double getNowSecondsTime();
	
	/**
	 * Returns the current playback time in frames
	 * 
	 * @return time of currently played sample
	 */
	long getNowFrameTime();

	/**
	 * Returns the earliest time in samples for which data
	 * is still accepted
	 * 
	 * @return earliest playback time for new data
	 */
	double getNextSecondsTime();
	
	/**
	 * Returns the earliest time in frames for which data
	 * is still accepted
	 * 
	 * @return earliest playback time for new data
	 */
	long getNextFrameTime();

	/**
	 * Converts from frame time to seconds time
	 * 
	 * @param frameTime frame time to convert
	 * @return corresponding seconds time
	 */
	double convertFrameToSecondsTime(long frameTime);

	/**
	 * Adjusts the frame time so that the given frame time
	 * coindices with the given seconds time
	 * 
	 * @param frameTime frame time corresponding to seconds time
	 * @param secondsTime seconds time corresponding to frame time
	 */
	void setFrameTime(long frameTime, double secondsTime);
}
