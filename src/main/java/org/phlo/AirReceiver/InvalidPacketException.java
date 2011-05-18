package org.phlo.AirReceiver;

@SuppressWarnings("serial")
public class InvalidPacketException extends ProtocolException {
	public InvalidPacketException(String message) {
		super(message);
	}
}
