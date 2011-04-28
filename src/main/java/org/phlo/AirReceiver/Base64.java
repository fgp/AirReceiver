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
