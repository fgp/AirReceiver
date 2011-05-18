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

import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.codec.rtsp.*;

/**
 * The RTSP methods required for RAOP/AirTunes
 */
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
