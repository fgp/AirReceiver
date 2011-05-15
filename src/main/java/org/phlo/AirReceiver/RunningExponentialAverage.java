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
