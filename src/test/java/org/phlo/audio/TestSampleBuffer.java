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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import org.junit.*;

public class TestSampleBuffer {
	@Test
	public void testInterleavedBigEndianUnsignedInteger16() {
		SampleDimensions byteDimensions = new SampleDimensions(4, 3);
		SampleDimensions sampleDimensions = new SampleDimensions(2, 4);

		SampleByteBufferFormat byteFormat = new SampleByteBufferFormat(SampleBufferLayout.Interleaved, ByteOrder.BIG_ENDIAN, SampleByteFormat.UnsignedInteger16);
		byte[] bytes = {
			/* channel 0 */				/* channel 1 */				/* channel 2 */				/* channel 3 */
			(byte)0x00, (byte) 0x00,	(byte)0x80, (byte)0x00,		(byte)0x00, (byte)0x01,		(byte)0x00, (byte)0x00,
			(byte)0x00, (byte) 0x00,	(byte)0xff, (byte)0xfe,		(byte)0x80, (byte)0x00,		(byte)0x00, (byte)0x00,
			(byte)0x00, (byte) 0x00,	(byte)0x00, (byte)0x01,		(byte)0xff, (byte)0xfe,		(byte)0x00, (byte)0x00,
		};
		int[] ints = {
			/* channel 0 */				/* channel 1 */				/* channel 2 */				/* channel 3 */
			0x0000,						0x8000,						0x0001,						0x0000,
			0x0000,						0xfffe,						0x8000,						0x0000,
			0x0000,						0x0001,						0xfffe,						0x0000
		};
		ByteBuffer bytesWrapped = byteFormat.wrapBytes(bytes);
		IntBuffer intsWrapped = IntBuffer.wrap(ints);
		float U = Math.scalb(1.0f, -15);
		float[][] values = {
			/* channel 0 */ { 0.0f,		 U * 0.5f,		1.0f - U,		-1.0f + U },
			/* channel 1 */ { 0.0f,		-1.0f + U,		U * 0.5f,		 1.0f - U },
		};
		
		SampleBuffer sampleBufferFromBytes = new SampleBuffer(sampleDimensions);
		sampleBufferFromBytes.slice(new SampleOffset(0, 1), null).copyFrom(
			bytesWrapped,
			byteDimensions,
			new SampleRange(new SampleOffset(1, 0), new SampleDimensions(2, 3)),
			byteFormat
		);
		
		SampleBuffer sampleBufferFromInts = new SampleBuffer(sampleDimensions);
		sampleBufferFromInts.slice(new SampleOffset(0, 1), null).copyFrom(
			intsWrapped,
			byteDimensions,
			new SampleRange(new SampleOffset(1, 0), new SampleDimensions(2, 3)),
			byteFormat.layout,
			byteFormat.sampleFormat.getSignedness()
		);
		
		for(int c=0; c < sampleDimensions.channels; ++c) {
			for(int s=0; s < sampleDimensions.samples; ++s) {
				Assert.assertEquals("[" + c + "," + s + "]", values[c][s], sampleBufferFromBytes.getSample(c,s), 1e-8);
				Assert.assertEquals("[" + c + "," + s + "]", values[c][s], sampleBufferFromInts.getSample(c,s), 1e-8);
			}
		}
		
		ByteBuffer byteBuffer = byteFormat.allocateBuffer(byteDimensions);
		sampleBufferFromBytes.slice(new SampleOffset(0, 1), new SampleDimensions(2, 3)).copyTo(
			byteBuffer,
			byteDimensions,
			new SampleOffset(1, 0),
			byteFormat
		);
		
		Assert.assertEquals(bytesWrapped.capacity(), byteBuffer.capacity());
		Assert.assertEquals(0, byteBuffer.compareTo(bytesWrapped));
	}	
	
	@Test
	public void testBandedLittleEndianSignedInteger16() {
		SampleDimensions byteDimensions = new SampleDimensions(4, 3);
		SampleDimensions sampleDimensions = new SampleDimensions(2, 4);

		SampleByteBufferFormat byteFormat = new SampleByteBufferFormat(SampleBufferLayout.Banded, ByteOrder.LITTLE_ENDIAN, SampleByteFormat.SignedInteger16);
		byte[] bytes = {
			/* channel 0 */	(byte)0x00, (byte)0x00,		(byte)0x00, (byte)0x00,			(byte)0x00, (byte)0x00,
			/* channel 1 */	(byte)0x00, (byte)0x80,		(byte)0xfe, (byte)0xff,			(byte)0x01, (byte)0x00,
			/* channel 2 */	(byte)0x01, (byte)0x00,		(byte)0x00, (byte)0x80,			(byte)0xfe, (byte)0xff,
			/* channel 3 */	(byte)0x00, (byte)0x00,		(byte)0x00, (byte)0x00,			(byte)0x00, (byte)0x00
		};
		int[] ints = {
			/* channel 0 */ 0x0000,						0x0000,							0x0000,
			/* channel 1 */ 0x8000,						0xfffe,							0x0001,
			/* channel 2 */ 0x0001,						0x8000,							0xfffe,
			/* channel 3 */ 0x0000,						0x0000,							0x0000,
		};
		ByteBuffer bytesWrapped = byteFormat.wrapBytes(bytes);
		IntBuffer intsWrapped = IntBuffer.wrap(ints);
		float U = Math.scalb(1.0f, -15);
		float[][] values = {
			/* channel 1 */ { 0.0f,		-1.0f + 0,		-U * 1.5f,		  U * 1.5f },
			/* channel 2 */ { 0.0f,		 U * 1.5f,		-1.0f + 0,		 -U * 1.5f },
		};
		
		SampleBuffer sampleBufferFromBytes = new SampleBuffer(sampleDimensions);
		sampleBufferFromBytes.slice(new SampleOffset(0, 1), null).copyFrom(
			bytesWrapped,
			byteDimensions,
			new SampleRange(new SampleOffset(1, 0), new SampleDimensions(2, 3)),
			byteFormat
		);

		SampleBuffer sampleBufferFromInts = new SampleBuffer(sampleDimensions);
		sampleBufferFromInts.slice(new SampleOffset(0, 1), null).copyFrom(
			intsWrapped,
			byteDimensions,
			new SampleRange(new SampleOffset(1, 0), new SampleDimensions(2, 3)),
			byteFormat.layout,
			byteFormat.sampleFormat.getSignedness()
		);

		for(int c=0; c < sampleDimensions.channels; ++c) {
			for(int s=0; s < sampleDimensions.samples; ++s) {
				Assert.assertEquals("[" + c + "," + s + "]", values[c][s], sampleBufferFromBytes.getSample(c,s), 1e-8);
				Assert.assertEquals("[" + c + "," + s + "]", values[c][s], sampleBufferFromInts.getSample(c,s), 1e-8);
			}
		}
		
		ByteBuffer byteBuffer = byteFormat.allocateBuffer(byteDimensions);
		sampleBufferFromBytes.slice(new SampleOffset(0, 1), new SampleDimensions(2, 3)).copyTo(
			byteBuffer,
			byteDimensions,
			new SampleOffset(1, 0),
			byteFormat
		);
		
		Assert.assertEquals(bytesWrapped.capacity(), byteBuffer.capacity());
		Assert.assertEquals(0, byteBuffer.compareTo(bytesWrapped));
	}
}
