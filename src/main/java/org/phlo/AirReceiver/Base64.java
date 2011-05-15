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
	public static byte[] decodePadded(String str)
		throws IOException
	{
		return net.iharder.Base64.decode(str);
	}
	
	public static byte[] decodeUnpadded(String base64)
		throws IOException
	{
		while (base64.length() % 4 != 0)
			base64 = base64.concat("=");
		
		return net.iharder.Base64.decode(base64);
	}
	
	public static String encodePadded(byte[] data)
	{
		return net.iharder.Base64.encodeBytes(data);
	}
	
	public static String encodeUnpadded(byte[] data) 
	{
		String str = net.iharder.Base64.encodeBytes(data);

		int pad = str.indexOf('=');
		if (pad >= 0)
			str = str.substring(0, pad);

		return str;
	}
}
