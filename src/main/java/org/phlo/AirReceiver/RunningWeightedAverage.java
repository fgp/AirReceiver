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

public class RunningWeightedAverage {
	public final int m_length;
	public final double m_values[];
	public final double m_weights[];
	public boolean m_empty = true;
	public int m_index = 0;
	public double m_average = Double.NaN;

	RunningWeightedAverage(final int windowLength) {
		m_length = windowLength;
		m_values = new double[m_length];
		m_weights = new double[m_length];
	}

	public boolean isEmpty() {
		return m_empty;
	}

	public void add(final double value, final double weight) {
		m_values[m_index] = value;
		m_weights[m_index] = weight;
		m_index = (m_index + 1) % m_length;
		m_average = Double.NaN;
		m_empty = false;
	}

	public double get() {
		if (!m_empty && Double.isNaN(m_average)) {
			double vsum = 0;
			double wsum = 0;
			for(int i=0; i < m_length; ++i) {
				vsum += m_values[i] * m_weights[i];
				wsum += m_weights[i];
			}
			if (wsum != 0.0)
				m_average = vsum / wsum;
		}

		return m_average;
	}
}
