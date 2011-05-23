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
