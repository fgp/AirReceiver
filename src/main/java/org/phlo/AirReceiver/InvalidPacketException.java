package org.phlo.AirReceiver;

@SuppressWarnings("serial")
public class InvalidPacketException extends ProtocolException {
	public InvalidPacketException(final String message) {
		super(message);
	}
}
