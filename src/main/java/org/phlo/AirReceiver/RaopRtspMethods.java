package org.phlo.AirReceiver;

import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.codec.rtsp.*;

public final class RaopRtspMethods {
	public static final HttpMethod ANNOUNCE = RtspMethods.ANNOUNCE;
	public static final HttpMethod GET_PARAMETER = RtspMethods.GET_PARAMETER;
	public static final HttpMethod FLUSH = new HttpMethod("FLUSH");
	public static final HttpMethod OPTIONS = RtspMethods.OPTIONS;
	public static final HttpMethod PAUSE = RtspMethods.PAUSE;
	public static final HttpMethod RECORD = RtspMethods.RECORD;
	public static final HttpMethod SETUP = RtspMethods.SETUP;
	public static final HttpMethod SET_PARAMETER = RtspMethods.SET_PARAMETER;
	public static final HttpMethod TEARDOWN = RtspMethods.TEARDOWN;
}
