package org.phlo.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.junit.*;

public class TestSampleBuffer {
	@Test
	public void testInterleavedBigEndianUnsignedInteger16() {
		SampleDimensions byteDimensions = new SampleDimensions(4, 3);
		SampleDimensions sampleDimensions = new SampleDimensions(2, 4);

		ByteFormat byteFormat = new ByteFormat(SampleLayout.Interleaved, ByteOrder.BIG_ENDIAN, SampleFormat.UnsignedInteger16);
		byte[] bytes = {
			/* channel 0 */				/* channel 1 */				/* channel 2 */				/* channel 3 */
			(byte)0x00, (byte) 0x00,	(byte)0x80, (byte)0x00,		(byte)0x00, (byte)0x01,		(byte)0x00, (byte)0x00,
			(byte)0x00, (byte) 0x00,	(byte)0xff, (byte)0xfe,		(byte)0x80, (byte)0x00,		(byte)0x00, (byte)0x00,
			(byte)0x00, (byte) 0x00,	(byte)0x00, (byte)0x01,		(byte)0xff, (byte)0xfe,		(byte)0x00, (byte)0x00,
		};
		ByteBuffer bytesWrapped = byteFormat.wrapBytes(bytes);
		float U = Math.scalb(1.0f, -15);
		float[][] values = {
			/* channel 0 */ { 0.0f,		 U * 0.5f,		1.0f - U,		-1.0f + U },
			/* channel 1 */ { 0.0f,		-1.0f + U,		U * 0.5f,		 1.0f - U },
		};
		
		SampleBuffer sampleBuffer = new SampleBuffer(sampleDimensions, 0);
		sampleBuffer.copyFrom(
			new SampleOffset(0, 1),
			bytesWrapped,
			byteDimensions,
			new SampleRange(new SampleOffset(1, 0), new SampleDimensions(2, 3)),
			byteFormat
		);
		
		for(int c=0; c < sampleDimensions.channels; ++c) {
			for(int s=0; s < sampleDimensions.samples; ++s) {
				Assert.assertEquals("[" + c + "," + s + "]", values[c][s], sampleBuffer.getSamples()[c][s], 1e-8);
			}
		}
		
		ByteBuffer byteBuffer = byteFormat.allocateBuffer(byteDimensions);
		sampleBuffer.copyTo(
			new SampleRange(new SampleOffset(0, 1), new SampleDimensions(2, 3)),
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

		ByteFormat byteFormat = new ByteFormat(SampleLayout.Banded, ByteOrder.LITTLE_ENDIAN, SampleFormat.SignedInteger16);
		byte[] bytes = {
			/* channel 0 */	(byte)0x00, (byte)0x00,		(byte)0x00, (byte)0x00,			(byte)0x00, (byte)0x00,
			/* channel 1 */	(byte)0x00, (byte)0x80,		(byte)0xfe, (byte)0xff,			(byte)0x01, (byte)0x00,
			/* channel 2 */	(byte)0x01, (byte)0x00,		(byte)0x00, (byte)0x80,			(byte)0xfe, (byte)0xff,
			/* channel 3 */	(byte)0x00, (byte)0x00,		(byte)0x00, (byte)0x00,			(byte)0x00, (byte)0x00
		};
		ByteBuffer bytesWrapped = byteFormat.wrapBytes(bytes);
		float U = Math.scalb(1.0f, -15);
		float[][] values = {
			/* channel 1 */ { 0.0f,		-1.0f + 0,		-U * 1.5f,		  U * 1.5f },
			/* channel 2 */ { 0.0f,		 U * 1.5f,		-1.0f + 0,		 -U * 1.5f },
		};
		
		SampleBuffer sampleBuffer = new SampleBuffer(sampleDimensions, 0);
		sampleBuffer.copyFrom(
			new SampleOffset(0, 1),
			bytesWrapped,
			byteDimensions,
			new SampleRange(new SampleOffset(1, 0), new SampleDimensions(2, 3)),
			byteFormat
		);
		
		for(int c=0; c < sampleDimensions.channels; ++c) {
			for(int s=0; s < sampleDimensions.samples; ++s) {
				Assert.assertEquals("[" + c + "," + s + "]", values[c][s], sampleBuffer.getSamples()[c][s], 1e-8);
			}
		}

		ByteBuffer byteBuffer = byteFormat.allocateBuffer(byteDimensions);
		sampleBuffer.copyTo(
			new SampleRange(new SampleOffset(0, 1), new SampleDimensions(2, 3)),
			byteBuffer,
			byteDimensions,
			new SampleOffset(1, 0),
			byteFormat
		);
		
		Assert.assertEquals(bytesWrapped.capacity(), byteBuffer.capacity());
		Assert.assertEquals(0, byteBuffer.compareTo(bytesWrapped));
	}
}
