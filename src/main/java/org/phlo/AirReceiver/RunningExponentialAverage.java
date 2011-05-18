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

public class RunningExponentialAverage {
	public double m_value = Double.NaN;

	public RunningExponentialAverage() {
		m_value = Double.NaN;
	}

	public RunningExponentialAverage(final double initialValue) {
		m_value = initialValue;
	}

	public void add(final double value, final double weight) {
		if (Double.isNaN(m_value)) {
			m_value = value;
		}
		else {
			m_value = value * weight + m_value * (1.0 - weight);
		}
	}

	public boolean isEmpty() {
		return Double.isNaN(m_value);
	}

	public double get() {
		return m_value;
	}
}
