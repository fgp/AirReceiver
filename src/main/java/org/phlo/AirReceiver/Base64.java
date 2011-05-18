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

import java.io.IOException;

public final class Base64 {
	/**
	 * Decodes Base64 data that is correctly padded with "="
	 * 
	 * @param str base64 data
	 * @return bytes
	 * @throws IOException if the data is invalid
	 */
	public static byte[] decodePadded(final String str)
		throws IOException
	{
		return net.iharder.Base64.decode(str);
	}

	/**
	 * Decodes Base64 data that is not padded with "="
	 * 
	 * @param str base64 data
	 * @return bytes
	 * @throws IOException if the data is invalid
	 */
	public static byte[] decodeUnpadded(String base64)
		throws IOException
	{
		while (base64.length() % 4 != 0)
			base64 = base64.concat("=");

		return net.iharder.Base64.decode(base64);
	}

	/**
	 * Encodes data to Base64 and padds with "="
	 * 
	 * @param data data to encode
	 * @return base64 encoded string
	 */
	public static String encodePadded(final byte[] data)
	{
		return net.iharder.Base64.encodeBytes(data);
	}

	/**
	 * Encodes data to Base64 but doesn't pad with "="
	 * 
	 * @param data data to encode
	 * @return base64 encoded string
	 */
	public static String encodeUnpadded(final byte[] data)
	{
		String str = net.iharder.Base64.encodeBytes(data);

		final int pad = str.indexOf('=');
		if (pad >= 0)
			str = str.substring(0, pad);

		return str;
	}
}
