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

public final class Latch<T> {
	private final Object m_monitor;
	private boolean m_ready = false;
	private T m_value = null;
	
	public Latch() {
		m_monitor = this;
	}
	
	public Latch(Object monitor) {
		m_monitor = monitor;
	}
	
	public void offer(T value) throws InterruptedException {
		synchronized(m_monitor) {
			/* Wait until the currently offered value is consumed */
			while (m_ready) {
				m_monitor.wait();
				
				/* If there is a concurrent consume() and a concurrent offer()
				 * the concurrent offer()'s notify() might have hit us instead
				 * of the consumer. In this case we re-notify() hoping to
				 * eventually hit the consume():
				 */
				if (m_ready)
					m_monitor.notify();
			}
			
			assert m_value == null;
			m_value = value;
			m_ready = true;
			m_monitor.notify();
		}
	}
	
	public T consume() throws InterruptedException {
		synchronized(m_monitor) {
			/* Wait until a value is offered */
			while (!m_ready) {
				m_monitor.wait();
				
				/* If there is a concurrent consume() and a concurrent offer()
				 * the concurrent consume()'s notify() might have hit us instead
				 * of the offer(). In this case we re-notify() hoping to
				 * eventually hit the offer():
				 */
				if (!m_ready)
					m_monitor.notify();
			}
			
			final T value = m_value;
			m_value = null;
			m_ready = false;
			m_monitor.notify();
			
			return value;
		}
	}
}
