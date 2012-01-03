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

public class SampleRange {
	public final SampleOffset offset;
	public final SampleDimensions size;

	public SampleRange(final SampleOffset _offset, final SampleDimensions _size) {
		offset = _offset;
		size = _size;
	}

	public SampleRange(final SampleDimensions _size) {
		offset = SampleOffset.Zero;
		size = _size;
	}
	
	public SampleRange slice(SampleOffset _offset, SampleDimensions _dimensions) {
		if (_offset == null)
			_offset = SampleOffset.Zero;
		
		if (_dimensions == null)
			_dimensions = size.reduce(_offset.channel, _offset.sample);

		size.assertContains(_offset, _dimensions);
		return new SampleRange(offset.add(_offset), _dimensions);
		
	}
	
	public SampleRange slice(SampleRange _range) {
		return slice(_range.offset, _range.size);
	}
	
	@Override
	public String toString() {
		return
			"[" +
				offset.channel + "..." + (offset.channel + size.channels) + ";" +
				offset.sample + "..." + (offset.sample + size.samples) +
			"]";
	}
}
