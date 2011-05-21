package org.phlo.audio;

public interface Block<ReturnType, ArgumentType> {
	public ReturnType block(ArgumentType argument);
}
