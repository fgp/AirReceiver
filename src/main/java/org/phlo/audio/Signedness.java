package org.phlo.audio;

public enum  Signedness {
	Signed {
		@Override public final int intToSignedInt(final int v) { return v; }
		@Override public final short shortToSignedShort(final short v) { return v; }
		@Override public final byte byteToSignedByte(final byte v) { return v; }

		@Override public final int intToUnsignedInt(final int v) { return v ^ 0x80000000; }
		@Override public final short shortToUnsignedShort(final short v) { return (short)((int)v - (int)Short.MIN_VALUE); }
		@Override public final byte byteToUnsignedByte(final byte v) { return (byte)((int)v - (int)Byte.MIN_VALUE); }

		@Override public final int intFromSignedInt(final int v) { return v; }
		@Override public final short shortFromSignedShort(final short v) { return v; }
		@Override public final byte byteFromSignedByte(final byte v) { return v; }

		@Override public final int intFromUnsignedInt(final int v) { return Unsigned.intToSignedInt(v); }
		@Override public final short shortFromUnsignedShort(final short v) { return Unsigned.shortToSignedShort(v); }
		@Override public final byte byteFromUnsignedByte(final byte v) { return Unsigned.byteToSignedByte(v); }

		@Override public final float intToFloat(final int v) { return v; }
		@Override public final float shortToFloat(final short v) { return v; }
		@Override public final float byteToFloat(final byte v) { return v; }

		@Override public final int intFromFloat(final float v) { return (int)v; }
		@Override public final short shortFromFloat(final float v) { return (short)Math.max(Short.MIN_VALUE, Math.min((int)v, Short.MAX_VALUE)); }
		@Override public final byte byteFromFloat(final float v) { return (byte)Math.max(Byte.MIN_VALUE, Math.min((int)v, Byte.MAX_VALUE)); }
	},
	Unsigned {
		@Override public final int intToSignedInt(final int v) { return v ^ Integer.MIN_VALUE; }
		@Override public final short shortToSignedShort(final short v) { return (short)((int)v + (int)Short.MIN_VALUE); }
		@Override public final byte byteToSignedByte(final byte v) { return (byte)((int)v + (int)Byte.MIN_VALUE); }

		@Override public final int intToUnsignedInt(final int v) { return v; }
		@Override public final short shortToUnsignedShort(final short v) { return v; }
		@Override public final byte byteToUnsignedByte(final byte v) { return v; }

		@Override public final int intFromSignedInt(final int v) { return Signed.intToUnsignedInt(v); }
		@Override public final short shortFromSignedShort(final short v) { return Signed.shortToUnsignedShort(v); }
		@Override public final byte byteFromSignedByte(final byte v) { return Signed.byteToUnsignedByte(v); }

		@Override public final int intFromUnsignedInt(final int v) { return v; }
		@Override public final short shortFromUnsignedShort(final short v) { return v; }
		@Override public final byte byteFromUnsignedByte(final byte v) { return v; }

		@Override public final float intToFloat(final int v) { return Signed.intToFloat(intToSignedInt(v)) - (float)Integer.MIN_VALUE; }
		@Override public final float shortToFloat(final short v) { return Signed.shortToFloat(shortToSignedShort(v)) - (float)Short.MIN_VALUE; }
		@Override public final float byteToFloat(final byte v) { return Signed.byteToFloat(byteToSignedByte(v)) - (float)Byte.MIN_VALUE; }

		@Override public final int intFromFloat(final float v) { return intFromSignedInt(Signed.intFromFloat(v + (float)Integer.MIN_VALUE)); }
		@Override public final short shortFromFloat(final float v) { return shortFromSignedShort(Signed.shortFromFloat(v + (float)Short.MIN_VALUE)); }
		@Override public final byte byteFromFloat(final float v) { return byteFromSignedByte(Signed.byteFromFloat(v + (float)Byte.MIN_VALUE)); }
	};

	public final float IntMin = intToFloat(intFromSignedInt(Integer.MIN_VALUE));
	public final float IntMax = intToFloat(intFromSignedInt(Integer.MAX_VALUE));
	public final float IntRange = IntMax - IntMin;
	public final float IntBias = 0.5f * IntMin + 0.5f * IntMax;

	public final float ShortMin = shortToFloat(shortFromSignedShort(Short.MIN_VALUE));
	public final float ShortMax = shortToFloat(shortFromSignedShort(Short.MAX_VALUE));
	public final float ShortRange = ShortMax - ShortMin;
	public final float ShortBias = 0.5f * ShortMin + 0.5f * ShortMax;

	public final float ByteMin = byteToFloat(byteFromSignedByte(Byte.MIN_VALUE));
	public final float ByteMax = byteToFloat(byteFromSignedByte(Byte.MAX_VALUE));
	public final float ByteRange = ByteMax - ByteMin;
	public final float ByteBias = 0.5f * ByteMin + 0.5f * ByteMax;

	public abstract int intToUnsignedInt(int v);
	public abstract short shortToUnsignedShort(short v);
	public abstract byte byteToUnsignedByte(byte v);

	public abstract int intToSignedInt(int v);
	public abstract short shortToSignedShort(short v);
	public abstract byte byteToSignedByte(byte v);

	public abstract int intFromUnsignedInt(int v);
	public abstract short shortFromUnsignedShort(short v);
	public abstract byte byteFromUnsignedByte(byte v);

	public abstract int intFromSignedInt(int v);
	public abstract short shortFromSignedShort(short v);
	public abstract byte byteFromSignedByte(byte v);

	public abstract float intToFloat(int v);
	public abstract float shortToFloat(short v);
	public abstract float byteToFloat(byte v);

	public abstract int intFromFloat(float v);
	public abstract short shortFromFloat(float v);
	public abstract byte byteFromFloat(float v);

	public final float intToNormalizedFloat(final int v) {
		return (intToFloat(v) - IntBias) * 2.0f / IntRange;
	}

	public final float shortToNormalizedFloat(final short v) {
		return (shortToFloat(v) - ShortBias) * 2.0f / ShortRange;
	}

	public final float byteToNormalizedFloat(final byte v) {
		return (byteToFloat(v) - ByteBias) * 2.0f / ByteRange;
	}

	public final int intFromNormalizedFloat(final float v) {
		return intFromFloat((v * IntRange / 2.0f) + IntBias);
	}

	public final short shortFromNormalizedFloat(final float v) {
		return shortFromFloat((v * ShortRange / 2.0f) + ShortBias);
	}

	public final byte byteFromNormalizedFloat(final float v) {
		return byteFromFloat((v * ByteRange / 2.0f) + ByteBias);
	}
}
