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

public final class SampleDimensions {
	public final int channels;
	public final int samples;

	public SampleDimensions(final int _channels, final int _samples) {
		if (_channels < 0)
			throw new IllegalArgumentException("channels must be greater or equal to zero");
		if (_samples < 0)
			throw new IllegalArgumentException("samples must be greater or equal to zero");

		channels = _channels;
		samples = _samples;
	}
	
	public SampleDimensions reduce(int _channels, int _samples) {
		assertContains(_channels, _samples);
		return new SampleDimensions(channels - _channels, samples - _samples);
	}
	
	public SampleDimensions intersect(final SampleDimensions other) {
		return new SampleDimensions(
			Math.min(channels, other.channels),
			Math.min(samples, other.samples)
		);
	}
	
	public int getTotalSamples() {
		return channels * samples;
	}
	
	public boolean contains(int _channels, int _samples) {
		return (_channels <= channels) && (_samples <= samples);
	}
	
	public boolean contains(SampleOffset offset, SampleDimensions dimensions) {
		return contains(offset.channel + dimensions.channels, offset.sample + dimensions.samples);
	}
	
	public boolean contains(SampleRange range) {
		return contains(range.offset, range.size);
	}
	
	public boolean contains(SampleOffset offset) {
		return (offset.channel < channels) && (offset.sample < samples);
	}
	
	public boolean contains(SampleDimensions dimensions) {
		return contains(dimensions.channels, dimensions.samples);
	}

	public void assertContains(int _channels, int _samples) {
		if (!contains(_channels, _samples))
			throw new IllegalArgumentException("Index (" + _channels + "," + _samples + ") exceeds dimensions " + this);
	}

	public void assertContains(SampleOffset offset, SampleDimensions dimensions) {
		if (!contains(offset, dimensions))
			throw new IllegalArgumentException("Dimensions " + dimensions + " at " + offset + " exceed dimensions " + this);
	}
	
	public void assertContains(SampleRange range) {
		if (!contains(range))
			throw new IllegalArgumentException("Range " + range + " exceeds dimensions " + this);
	}

	public void assertContains(SampleOffset offset) {
		if (!contains(offset))
			throw new IllegalArgumentException("Offset " + offset + " exceeds dimensions " + this);
	}

	public void assertContains(SampleDimensions dimensions) {
		if (!contains(dimensions))
			throw new IllegalArgumentException("Dimensions " + dimensions + " exceeds dimensions " + this);
	}

	@Override
	public String toString() {
		return "[" + channels + ";" + samples + "]";
	}
}
