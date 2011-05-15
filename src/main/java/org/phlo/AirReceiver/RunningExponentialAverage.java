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
	public final double m_timeConstant;
	public double m_value = Double.NaN;
	
	public RunningExponentialAverage(double timeConstant, double initialValue) {
		m_timeConstant = timeConstant;
		m_value = initialValue;
	}
	
	public void add(double value) {
		if (Double.isNaN(value)) {
			m_value = value;
		}
		else {
			/* Numerically optimized restatement of
			 * m_v = m_v * tc + (1.0 - tc) * v;
			 */
			m_value = value + m_timeConstant * (m_value - value);
		}
	}
	
	public double get() {
		return m_value;
	}
}
