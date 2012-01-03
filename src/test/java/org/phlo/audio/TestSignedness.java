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

import org.junit.*;

public class TestSignedness {
	@Test
	public void testSignedInt() {
		Assert.assertEquals(0, Signedness.Signed.intToUnsignedInt(Integer.MIN_VALUE));
		Assert.assertEquals(0x7fffffff, Signedness.Signed.intToUnsignedInt(-1));
		Assert.assertEquals(0x80000000, Signedness.Signed.intToUnsignedInt(0));
		Assert.assertEquals(0x80000001, Signedness.Signed.intToUnsignedInt(1));
		Assert.assertEquals(0xffffffff, Signedness.Signed.intToUnsignedInt(Integer.MAX_VALUE));

		Assert.assertEquals(Integer.MIN_VALUE, Signedness.Signed.intToSignedInt(Integer.MIN_VALUE));
		Assert.assertEquals(-1, Signedness.Signed.intToSignedInt(-1));
		Assert.assertEquals(0, Signedness.Signed.intToSignedInt(0));
		Assert.assertEquals(1, Signedness.Signed.intToSignedInt(1));
		Assert.assertEquals(Integer.MAX_VALUE, Signedness.Signed.intToSignedInt(Integer.MAX_VALUE));

		Assert.assertEquals(Integer.MIN_VALUE, Signedness.Signed.intFromUnsignedInt(0x0));
		Assert.assertEquals(-1, Signedness.Signed.intFromUnsignedInt(0x7fffffff));
		Assert.assertEquals(0, Signedness.Signed.intFromUnsignedInt(0x80000000));
		Assert.assertEquals(1, Signedness.Signed.intFromUnsignedInt(0x80000001));
		Assert.assertEquals(Integer.MAX_VALUE, Signedness.Signed.intFromUnsignedInt(0xffffffff));

		Assert.assertEquals(Integer.MIN_VALUE, Signedness.Signed.intFromSignedInt(Integer.MIN_VALUE));
		Assert.assertEquals(-1, Signedness.Signed.intFromSignedInt(-1));
		Assert.assertEquals(0, Signedness.Signed.intFromSignedInt(0));
		Assert.assertEquals(1, Signedness.Signed.intFromSignedInt(1));
		Assert.assertEquals(Integer.MAX_VALUE, Signedness.Signed.intFromSignedInt(Integer.MAX_VALUE));

		Assert.assertEquals((float)Integer.MIN_VALUE, Signedness.Signed.intToFloat(Integer.MIN_VALUE), 0.0f);
		Assert.assertEquals(-1.0f, Signedness.Signed.intToFloat(-1), 0.0f);
		Assert.assertEquals(0.0f, Signedness.Signed.intToFloat(0), 0.0f);
		Assert.assertEquals(1.0f, Signedness.Signed.intToFloat(1), 0.0f);
		Assert.assertEquals((float)Integer.MAX_VALUE, Signedness.Signed.intToFloat(Integer.MAX_VALUE), 0.0f);

		Assert.assertEquals(Integer.MIN_VALUE, Signedness.Signed.intFromFloat((float)Integer.MIN_VALUE));
		Assert.assertEquals(-1, Signedness.Signed.intFromFloat(-1.0f));
		Assert.assertEquals(0, Signedness.Signed.intFromFloat(0.0f));
		Assert.assertEquals(1, Signedness.Signed.intFromFloat(1.0f));
		Assert.assertEquals(Integer.MAX_VALUE, Signedness.Signed.intFromFloat((float)Integer.MAX_VALUE));

		Assert.assertEquals(-1.0f, Signedness.Signed.intToNormalizedFloat(Integer.MIN_VALUE), 0.0f);
		Assert.assertEquals(0.0f, Signedness.Signed.intToNormalizedFloat(0), 0.0f);
		Assert.assertEquals(1.0f, Signedness.Signed.intToNormalizedFloat(Integer.MAX_VALUE), 0.0f);

		Assert.assertEquals(Integer.MIN_VALUE, Signedness.Signed.intFromNormalizedFloat(-1.0f));
		Assert.assertEquals(Integer.MIN_VALUE + 128, Signedness.Signed.intFromNormalizedFloat(Math.nextUp(-1.0f)));
		Assert.assertEquals(0, Signedness.Signed.intFromNormalizedFloat(0.0f));
		Assert.assertEquals(Integer.MAX_VALUE - 127, Signedness.Signed.intFromNormalizedFloat(-Math.nextUp(-1.0f)));
		Assert.assertEquals(Integer.MAX_VALUE, Signedness.Signed.intFromNormalizedFloat(1.0f));
	}
	
	@Test
	public void testSignedShort() {
		Assert.assertEquals((short)0, Signedness.Signed.shortToUnsignedShort(Short.MIN_VALUE));
		Assert.assertEquals((short)0x7fff, Signedness.Signed.shortToUnsignedShort((short)-1));
		Assert.assertEquals((short)0x8000, Signedness.Signed.shortToUnsignedShort((short)0));
		Assert.assertEquals((short)0x8001, Signedness.Signed.shortToUnsignedShort((short)1));
		Assert.assertEquals((short)0xffff, Signedness.Signed.shortToUnsignedShort(Short.MAX_VALUE));

		Assert.assertEquals(Short.MIN_VALUE, Signedness.Signed.shortToSignedShort(Short.MIN_VALUE));
		Assert.assertEquals((short)-1, Signedness.Signed.shortToSignedShort((short)-1));
		Assert.assertEquals((short)0, Signedness.Signed.shortToSignedShort((short)0));
		Assert.assertEquals((short)1, Signedness.Signed.shortToSignedShort((short)1));
		Assert.assertEquals(Short.MAX_VALUE, Signedness.Signed.shortToSignedShort(Short.MAX_VALUE));

		Assert.assertEquals(Short.MIN_VALUE, Signedness.Signed.shortFromUnsignedShort((short)0x0));
		Assert.assertEquals((short)-1, Signedness.Signed.shortFromUnsignedShort((short)0x7fff));
		Assert.assertEquals((short)0, Signedness.Signed.shortFromUnsignedShort((short)0x8000));
		Assert.assertEquals((short)1, Signedness.Signed.shortFromUnsignedShort((short)0x8001));
		Assert.assertEquals(Short.MAX_VALUE, Signedness.Signed.shortFromUnsignedShort((short)0xffff));

		Assert.assertEquals(Short.MIN_VALUE, Signedness.Signed.shortFromSignedShort(Short.MIN_VALUE));
		Assert.assertEquals(-1, Signedness.Signed.shortFromSignedShort((short)-1));
		Assert.assertEquals(0, Signedness.Signed.shortFromSignedShort((short)0));
		Assert.assertEquals(1, Signedness.Signed.shortFromSignedShort((short)1));
		Assert.assertEquals(Short.MAX_VALUE, Signedness.Signed.shortFromSignedShort(Short.MAX_VALUE));

		Assert.assertEquals((float)Short.MIN_VALUE, Signedness.Signed.shortToFloat(Short.MIN_VALUE), 0.0f);
		Assert.assertEquals(-1.0f, Signedness.Signed.shortToFloat((short)-1), 0.0f);
		Assert.assertEquals(0.0f, Signedness.Signed.shortToFloat((short)0), 0.0f);
		Assert.assertEquals(1.0f, Signedness.Signed.shortToFloat((short)1), 0.0f);
		Assert.assertEquals((float)Short.MAX_VALUE, Signedness.Signed.shortToFloat(Short.MAX_VALUE), 0.0f);

		Assert.assertEquals(Short.MIN_VALUE, Signedness.Signed.shortFromFloat((float)Short.MIN_VALUE));
		Assert.assertEquals(-1, Signedness.Signed.shortFromFloat(-1.0f));
		Assert.assertEquals(0, Signedness.Signed.shortFromFloat(0.0f));
		Assert.assertEquals(1, Signedness.Signed.shortFromFloat(1.0f));
		Assert.assertEquals(Short.MAX_VALUE, Signedness.Signed.shortFromFloat((float)Short.MAX_VALUE));

		Assert.assertEquals(-1.0f, Signedness.Signed.shortToNormalizedFloat(Short.MIN_VALUE), 0.0f);
		Assert.assertEquals(Math.scalb(1.0f, -16), Signedness.Signed.shortToNormalizedFloat((short)0), 1e-8);
		Assert.assertEquals(1.0f, Signedness.Signed.shortToNormalizedFloat(Short.MAX_VALUE), 0.0f);
		
		Assert.assertEquals(Short.MIN_VALUE, Signedness.Signed.shortFromNormalizedFloat(-1.0f));
		Assert.assertEquals(Short.MIN_VALUE + 1, Signedness.Signed.shortFromNormalizedFloat(-1.0f + Math.scalb(1.0f, -16)));
		Assert.assertEquals((short)0, Signedness.Signed.shortFromNormalizedFloat(-Math.scalb(1.0f, -17)));
		Assert.assertEquals((short)0, Signedness.Signed.shortFromNormalizedFloat(0.0f));
		Assert.assertEquals((short)0, Signedness.Signed.shortFromNormalizedFloat(Math.scalb(1.0f, -17)));
		Assert.assertEquals(Short.MAX_VALUE - 1, Signedness.Signed.shortFromNormalizedFloat(1.0f - Math.scalb(1.0f, -16)));
		Assert.assertEquals(Short.MAX_VALUE, Signedness.Signed.shortFromNormalizedFloat(1.0f));
	}
	
	@Test
	public void testSignedByte() {
		Assert.assertEquals((byte)0, Signedness.Signed.byteToUnsignedByte(Byte.MIN_VALUE));
		Assert.assertEquals((byte)0x7f, Signedness.Signed.byteToUnsignedByte((byte)-1));
		Assert.assertEquals((byte)0x80, Signedness.Signed.byteToUnsignedByte((byte)0));
		Assert.assertEquals((byte)0x81, Signedness.Signed.byteToUnsignedByte((byte)1));
		Assert.assertEquals((byte)0xff, Signedness.Signed.byteToUnsignedByte(Byte.MAX_VALUE));

		Assert.assertEquals(Byte.MIN_VALUE, Signedness.Signed.byteToSignedByte(Byte.MIN_VALUE));
		Assert.assertEquals((byte)-1, Signedness.Signed.byteToSignedByte((byte)-1));
		Assert.assertEquals((byte)0, Signedness.Signed.byteToSignedByte((byte)0));
		Assert.assertEquals((byte)1, Signedness.Signed.byteToSignedByte((byte)1));
		Assert.assertEquals(Byte.MAX_VALUE, Signedness.Signed.byteToSignedByte(Byte.MAX_VALUE));

		Assert.assertEquals(Byte.MIN_VALUE, Signedness.Signed.byteFromUnsignedByte((byte)0x0));
		Assert.assertEquals((byte)-1, Signedness.Signed.byteFromUnsignedByte((byte)0x7f));
		Assert.assertEquals((byte)0, Signedness.Signed.byteFromUnsignedByte((byte)0x80));
		Assert.assertEquals((byte)1, Signedness.Signed.byteFromUnsignedByte((byte)0x81));
		Assert.assertEquals(Byte.MAX_VALUE, Signedness.Signed.byteFromUnsignedByte((byte)0xff));

		Assert.assertEquals(Byte.MIN_VALUE, Signedness.Signed.byteFromSignedByte(Byte.MIN_VALUE));
		Assert.assertEquals(-1, Signedness.Signed.byteFromSignedByte((byte)-1));
		Assert.assertEquals(0, Signedness.Signed.byteFromSignedByte((byte)0));
		Assert.assertEquals(1, Signedness.Signed.byteFromSignedByte((byte)1));
		Assert.assertEquals(Byte.MAX_VALUE, Signedness.Signed.byteFromSignedByte(Byte.MAX_VALUE));

		Assert.assertEquals((float)Byte.MIN_VALUE, Signedness.Signed.byteToFloat(Byte.MIN_VALUE), 0.0f);
		Assert.assertEquals(-1.0f, Signedness.Signed.byteToFloat((byte)-1), 0.0f);
		Assert.assertEquals(0.0f, Signedness.Signed.byteToFloat((byte)0), 0.0f);
		Assert.assertEquals(1.0f, Signedness.Signed.byteToFloat((byte)1), 0.0f);
		Assert.assertEquals((float)Byte.MAX_VALUE, Signedness.Signed.byteToFloat(Byte.MAX_VALUE), 0.0f);

		Assert.assertEquals(Byte.MIN_VALUE, Signedness.Signed.byteFromFloat((float)Byte.MIN_VALUE));
		Assert.assertEquals(-1, Signedness.Signed.byteFromFloat(-1.0f));
		Assert.assertEquals(0, Signedness.Signed.byteFromFloat(0.0f));
		Assert.assertEquals(1, Signedness.Signed.byteFromFloat(1.0f));
		Assert.assertEquals(Byte.MAX_VALUE, Signedness.Signed.byteFromFloat((float)Byte.MAX_VALUE));
		
		Assert.assertEquals(-1.0f, Signedness.Signed.byteToNormalizedFloat(Byte.MIN_VALUE), 0.0f);
		Assert.assertEquals(Math.scalb(1.0f, -8), Signedness.Signed.byteToNormalizedFloat((byte)0), 1e-4);
		Assert.assertEquals(1.0f, Signedness.Signed.byteToNormalizedFloat(Byte.MAX_VALUE), 0.0f);

		Assert.assertEquals(Byte.MIN_VALUE, Signedness.Signed.byteFromNormalizedFloat(-1.0f));
		Assert.assertEquals(Byte.MIN_VALUE + 1, Signedness.Signed.byteFromNormalizedFloat(-1.0f + Math.scalb(1.0f, -8)));
		Assert.assertEquals((byte)0, Signedness.Signed.byteFromNormalizedFloat(-Math.scalb(1.0f, -9)));
		Assert.assertEquals((byte)0, Signedness.Signed.byteFromNormalizedFloat(0.0f));
		Assert.assertEquals((byte)0, Signedness.Signed.byteFromNormalizedFloat(Math.scalb(1.0f, -9)));
		Assert.assertEquals(Byte.MAX_VALUE - 1, Signedness.Signed.byteFromNormalizedFloat(1.0f - Math.scalb(1.0f, -8)));
		Assert.assertEquals(Byte.MAX_VALUE, Signedness.Signed.byteFromNormalizedFloat(1.0f));
	}
	
	@Test
	public void testUnsignedInt() {
		Assert.assertEquals(0x0, Signedness.Unsigned.intToUnsignedInt(0x0));
		Assert.assertEquals(0x7fffffff, Signedness.Unsigned.intToUnsignedInt(0x7fffffff));
		Assert.assertEquals(0x80000000, Signedness.Unsigned.intToUnsignedInt(0x80000000));
		Assert.assertEquals(0x80000001, Signedness.Unsigned.intToUnsignedInt(0x80000001));
		Assert.assertEquals(0xffffffff, Signedness.Unsigned.intToUnsignedInt(0xffffffff));

		Assert.assertEquals(Integer.MIN_VALUE, Signedness.Unsigned.intToSignedInt(0x0));
		Assert.assertEquals(-1, Signedness.Unsigned.intToSignedInt(0x7fffffff));
		Assert.assertEquals(0, Signedness.Unsigned.intToSignedInt(0x80000000));
		Assert.assertEquals(1, Signedness.Unsigned.intToSignedInt(0x80000001));
		Assert.assertEquals(Integer.MAX_VALUE, Signedness.Unsigned.intToSignedInt(0xffffffff));

		Assert.assertEquals(0x0, Signedness.Unsigned.intFromUnsignedInt(0x0));
		Assert.assertEquals(0x7fffffff, Signedness.Unsigned.intFromUnsignedInt(0x7fffffff));
		Assert.assertEquals(0x80000000, Signedness.Unsigned.intFromUnsignedInt(0x80000000));
		Assert.assertEquals(0x80000001, Signedness.Unsigned.intFromUnsignedInt(0x80000001));
		Assert.assertEquals(0xffffffff, Signedness.Unsigned.intFromUnsignedInt(0xffffffff));

		Assert.assertEquals(0, Signedness.Unsigned.intFromSignedInt(Integer.MIN_VALUE));
		Assert.assertEquals(0x7fffffff, Signedness.Unsigned.intFromSignedInt(-1));
		Assert.assertEquals(0x80000000, Signedness.Unsigned.intFromSignedInt(0));
		Assert.assertEquals(0x80000001, Signedness.Unsigned.intFromSignedInt(1));
		Assert.assertEquals(0xffffffff, Signedness.Unsigned.intFromSignedInt(Integer.MAX_VALUE));

		Assert.assertEquals(0.0f, Signedness.Unsigned.intToFloat(0x0), 0.0f);
		Assert.assertEquals(2147483647.0f, Signedness.Unsigned.intToFloat(0x7fffffff), 0.0f);
		Assert.assertEquals(2147483648.0f, Signedness.Unsigned.intToFloat(0x80000000), 0.0f);
		Assert.assertEquals(2147483649.0f, Signedness.Unsigned.intToFloat(0x80000001), 0.0f);
		Assert.assertEquals(4294967295.0f, Signedness.Unsigned.intToFloat(0xffffffff), 0.0f);
		
		/* The cases 0x7fffffff and 0x80000001 would because floats store only 24 significant
		 * digits, not 32. This is ok, so we simply leave these cases out
		 */
		Assert.assertEquals(0x0, Signedness.Unsigned.intFromFloat(0.0f));
		Assert.assertEquals(0x80000000, Signedness.Unsigned.intFromFloat(2147483648.0f));
		Assert.assertEquals(0xffffffff, Signedness.Unsigned.intFromFloat(4294967295.0f));
		
		Assert.assertEquals(-1.0f, Signedness.Unsigned.intToNormalizedFloat(0x0), 0.0f);
		Assert.assertEquals(0.0f, Signedness.Unsigned.intToNormalizedFloat(0x80000000), 0.0f);
		Assert.assertEquals(1.0f, Signedness.Unsigned.intToNormalizedFloat(0xffffffff), 0.0f);
		
		Assert.assertEquals(0x0, Signedness.Unsigned.intFromNormalizedFloat(-1.0f));
		Assert.assertEquals(0x00000080, Signedness.Unsigned.intFromNormalizedFloat(Math.nextUp(-1.0f)));
		Assert.assertEquals(0x80000000, Signedness.Unsigned.intFromNormalizedFloat(0.0f));
		Assert.assertEquals(0xffffff00, Signedness.Unsigned.intFromNormalizedFloat(-Math.nextUp(Math.nextUp(-1.0f))));
		Assert.assertEquals(0xffffffff, Signedness.Unsigned.intFromNormalizedFloat(-Math.nextUp(-1.0f)));
		Assert.assertEquals(0xffffffff, Signedness.Unsigned.intFromNormalizedFloat(1.0f));
	}
	
	@Test
	public void testUnsignedShort() {
		Assert.assertEquals((short)0x0, Signedness.Unsigned.shortToUnsignedShort((short)0x0));
		Assert.assertEquals((short)0x7fff, Signedness.Unsigned.shortToUnsignedShort((short)0x7fff));
		Assert.assertEquals((short)0x8000, Signedness.Unsigned.shortToUnsignedShort((short)0x8000));
		Assert.assertEquals((short)0x8001, Signedness.Unsigned.shortToUnsignedShort((short)0x8001));
		Assert.assertEquals((short)0xffff, Signedness.Unsigned.shortToUnsignedShort((short)0xffff));

		Assert.assertEquals(Short.MIN_VALUE, Signedness.Unsigned.shortToSignedShort((short)0x0));
		Assert.assertEquals((short)-1, Signedness.Unsigned.shortToSignedShort((short)0x7fff));
		Assert.assertEquals((short)0, Signedness.Unsigned.shortToSignedShort((short)0x8000));
		Assert.assertEquals((short)1, Signedness.Unsigned.shortToSignedShort((short)0x8001));
		Assert.assertEquals(Short.MAX_VALUE, Signedness.Unsigned.shortToSignedShort((short)0xffff));

		Assert.assertEquals((short)0x0, Signedness.Unsigned.shortFromUnsignedShort((short)0x0));
		Assert.assertEquals((short)0x7fff, Signedness.Unsigned.shortFromUnsignedShort((short)0x7fff));
		Assert.assertEquals((short)0x8000, Signedness.Unsigned.shortFromUnsignedShort((short)0x8000));
		Assert.assertEquals((short)0x8001, Signedness.Unsigned.shortFromUnsignedShort((short)0x8001));
		Assert.assertEquals((short)0xffff, Signedness.Unsigned.shortFromUnsignedShort((short)0xffff));

		Assert.assertEquals((short)0, Signedness.Unsigned.shortFromSignedShort(Short.MIN_VALUE));
		Assert.assertEquals((short)0x7fff, Signedness.Unsigned.shortFromSignedShort((short)-1));
		Assert.assertEquals((short)0x8000, Signedness.Unsigned.shortFromSignedShort((short)0));
		Assert.assertEquals((short)0x8001, Signedness.Unsigned.shortFromSignedShort((short)1));
		Assert.assertEquals((short)0xffff, Signedness.Unsigned.shortFromSignedShort(Short.MAX_VALUE));

		Assert.assertEquals(0.0f, Signedness.Unsigned.shortToFloat((short)0x0), 0.0f);
		Assert.assertEquals(32767.0f, Signedness.Unsigned.shortToFloat((short)0x7fff), 0.0f);
		Assert.assertEquals(32768.0f, Signedness.Unsigned.shortToFloat((short)0x8000), 0.0f);
		Assert.assertEquals(32769.0f, Signedness.Unsigned.shortToFloat((short)0x8001), 0.0f);
		Assert.assertEquals(65535.0f, Signedness.Unsigned.shortToFloat((short)0xffff), 0.0f);
		
		Assert.assertEquals((short)0x0, Signedness.Unsigned.shortFromFloat(0.0f));
		Assert.assertEquals((short)0x7fff, Signedness.Unsigned.shortFromFloat(32767.0f));
		Assert.assertEquals((short)0x8000, Signedness.Unsigned.shortFromFloat(32768.0f));
		Assert.assertEquals((short)0x8001, Signedness.Unsigned.shortFromFloat(32769.0f));
		Assert.assertEquals((short)0xffff, Signedness.Unsigned.shortFromFloat(65535.0f));
		
		Assert.assertEquals(-1.0f, Signedness.Unsigned.shortToNormalizedFloat((short)0x0), 0.0f);
		Assert.assertEquals(Math.scalb(1.0f, -16), Signedness.Unsigned.shortToNormalizedFloat((short)0x8000), 1e-8);
		Assert.assertEquals(1.0f, Signedness.Unsigned.shortToNormalizedFloat((short)0xffff), 0.0f);
		
		Assert.assertEquals(0x0, Signedness.Unsigned.shortFromNormalizedFloat(-1.0f));
		Assert.assertEquals(0x1, Signedness.Unsigned.shortFromNormalizedFloat(-1.0f + Math.scalb(1.0f, -16)));
		Assert.assertEquals((short)0x8000, Signedness.Unsigned.shortFromNormalizedFloat(-Math.scalb(1.0f, -17)));
		Assert.assertEquals((short)0x8000, Signedness.Unsigned.shortFromNormalizedFloat(0.0f));
		Assert.assertEquals((short)0x8000, Signedness.Unsigned.shortFromNormalizedFloat(Math.scalb(1.0f, -17)));
		Assert.assertEquals((short)0xfffe, Signedness.Unsigned.shortFromNormalizedFloat(1.0f - Math.scalb(1.0f, -16)));
		Assert.assertEquals((short)0xffff, Signedness.Unsigned.shortFromNormalizedFloat(1.0f));
	}	
	
	@Test
	public void testUnsignedByte() {
		Assert.assertEquals((byte)0x0, Signedness.Unsigned.byteToUnsignedByte((byte)0x0));
		Assert.assertEquals((byte)0x7f, Signedness.Unsigned.byteToUnsignedByte((byte)0x7f));
		Assert.assertEquals((byte)0x80, Signedness.Unsigned.byteToUnsignedByte((byte)0x80));
		Assert.assertEquals((byte)0x81, Signedness.Unsigned.byteToUnsignedByte((byte)0x81));
		Assert.assertEquals((byte)0xff, Signedness.Unsigned.byteToUnsignedByte((byte)0xff));

		Assert.assertEquals(Byte.MIN_VALUE, Signedness.Unsigned.byteToSignedByte((byte)0x0));
		Assert.assertEquals((byte)-1, Signedness.Unsigned.byteToSignedByte((byte)0x7f));
		Assert.assertEquals((byte)0, Signedness.Unsigned.byteToSignedByte((byte)0x80));
		Assert.assertEquals((byte)1, Signedness.Unsigned.byteToSignedByte((byte)0x81));
		Assert.assertEquals(Byte.MAX_VALUE, Signedness.Unsigned.byteToSignedByte((byte)0xff));

		Assert.assertEquals((byte)0x0, Signedness.Unsigned.byteFromUnsignedByte((byte)0x0));
		Assert.assertEquals((byte)0x7f, Signedness.Unsigned.byteFromUnsignedByte((byte)0x7f));
		Assert.assertEquals((byte)0x80, Signedness.Unsigned.byteFromUnsignedByte((byte)0x80));
		Assert.assertEquals((byte)0x81, Signedness.Unsigned.byteFromUnsignedByte((byte)0x81));
		Assert.assertEquals((byte)0xff, Signedness.Unsigned.byteFromUnsignedByte((byte)0xff));

		Assert.assertEquals((byte)0, Signedness.Unsigned.byteFromSignedByte(Byte.MIN_VALUE));
		Assert.assertEquals((byte)0x7f, Signedness.Unsigned.byteFromSignedByte((byte)-1));
		Assert.assertEquals((byte)0x80, Signedness.Unsigned.byteFromSignedByte((byte)0));
		Assert.assertEquals((byte)0x81, Signedness.Unsigned.byteFromSignedByte((byte)1));
		Assert.assertEquals((byte)0xff, Signedness.Unsigned.byteFromSignedByte(Byte.MAX_VALUE));

		Assert.assertEquals(0.0f, Signedness.Unsigned.byteToFloat((byte)0x0), 0.0f);
		Assert.assertEquals(127.0f, Signedness.Unsigned.byteToFloat((byte)0x7f), 0.0f);
		Assert.assertEquals(128.0f, Signedness.Unsigned.byteToFloat((byte)0x80), 0.0f);
		Assert.assertEquals(129.0f, Signedness.Unsigned.byteToFloat((byte)0x81), 0.0f);
		Assert.assertEquals(255.0f, Signedness.Unsigned.byteToFloat((byte)0xff), 0.0f);
		
		Assert.assertEquals((byte)0x0, Signedness.Unsigned.byteFromFloat(0.0f));
		Assert.assertEquals((byte)0x7f, Signedness.Unsigned.byteFromFloat(127.0f));
		Assert.assertEquals((byte)0x80, Signedness.Unsigned.byteFromFloat(128.0f));
		Assert.assertEquals((byte)0x81, Signedness.Unsigned.byteFromFloat(129.0f));
		Assert.assertEquals((byte)0xff, Signedness.Unsigned.byteFromFloat(255.0f));
		
		Assert.assertEquals(-1.0f, Signedness.Unsigned.byteToNormalizedFloat((byte)0x0), 0.0f);
		Assert.assertEquals(Math.scalb(1.0f, -8), Signedness.Unsigned.byteToNormalizedFloat((byte)0x80), 1e-4);
		Assert.assertEquals(1.0f, Signedness.Unsigned.byteToNormalizedFloat((byte)0xff), 0.0f);
		
		Assert.assertEquals(0x0, Signedness.Unsigned.byteFromNormalizedFloat(-1.0f));
		Assert.assertEquals(0x1, Signedness.Unsigned.byteFromNormalizedFloat(-1.0f + Math.scalb(1.0f, -8)));
		Assert.assertEquals((byte)0x80, Signedness.Unsigned.byteFromNormalizedFloat(-Math.scalb(1.0f, -9)));
		Assert.assertEquals((byte)0x80, Signedness.Unsigned.byteFromNormalizedFloat(0.0f));
		Assert.assertEquals((byte)0x80, Signedness.Unsigned.byteFromNormalizedFloat(Math.scalb(1.0f, -9)));
		Assert.assertEquals((byte)0xfe, Signedness.Unsigned.byteFromNormalizedFloat(1.0f - Math.scalb(1.0f, -8)));
		Assert.assertEquals((byte)0xff, Signedness.Unsigned.byteFromNormalizedFloat(1.0f));
	}
}
