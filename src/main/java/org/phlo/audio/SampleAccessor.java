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

/**
 * Provides access to samples in a buffer via
 * their absolute position in the buffer.
 * <p>
 * The caller is required to know the layout
 * of the samples, since the methods in this
 * interface don't allow specifying the
 * channel and sample index separately. For
 * this reason, it is usually preferable to
 * use a {@link SampleIndexedAccessor} instance
 * instead.
 * 
 * @see {@link SampleIndexedAccessor}
 */
public interface SampleAccessor {
	/**
	 * Return the value of the sample {@code index}
	 * bytes into the buffer
	 */
	float getSample(int index);
	
	/**
	 * Sets the value of the sample {code index}
	 * bytes into the buffer.
	 */
	void setSample(int index, float value);
}
