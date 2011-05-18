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
 * Computes an exponential running average 
 * over a series of values.
 */
public class RunningExponentialAverage {
	public double m_value = Double.NaN;

	/**
	 * Create an exponential average without an initial value
	 */
	public RunningExponentialAverage() {
		m_value = Double.NaN;
	}

	/**
	 * Create an exponential average with the given initial value
	 */
	public RunningExponentialAverage(final double initialValue) {
		m_value = initialValue;
	}

	/**
	 * Add the value to the average, weighting it with the giveen weight.
	 * 
	 * @param value the value to add
	 * @param weight the value's weight between 0 and 1.
	 */
	public void add(final double value, final double weight) {
		if (Double.isNaN(m_value)) {
			m_value = value;
		}
		else {
			m_value = value * weight + m_value * (1.0 - weight);
		}
	}

	/**
	 * Return true until {@link #add(double, double)} has been called
	 * at least once for instances initialized without an initial value.
	 * Otherwise, always returns false.
	 */
	public boolean isEmpty() {
		return Double.isNaN(m_value);
	}

	/**
	 * Returns the current exponential average
	 * @return exponential average
	 */
	public double get() {
		return m_value;
	}
}
