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
	
	public SampleOffset add(SampleOffset other) {
		return new SampleOffset(channel + other.channel, sample + other.sample);
	}
	
	@Override
	public String toString() {
		return "[" + channel + ";" + sample + "]";
	}
}
