package com.zeroseek.worldgen.math;

import net.minecraft.util.Mth;

/**
 * Compact Sine Lookup Table for ZeroSeek Engine.
 * Reduces the lookup table from 256 KB to 64 KB (16K entries), fitting completely into CPU L1/L2 cache.
 * Values are bit-for-bit identical to vanilla Mth.
 */
public final class CompactSineLUT {
    private static final int[] SINE_TABLE_INT = new int[16384 + 1];
    private static final float SINE_TABLE_MIDPOINT;

    static {
        for (int i = 0; i < SINE_TABLE_INT.length; i++) {
            SINE_TABLE_INT[i] = Float.floatToRawIntBits(Mth.SIN[i]);
        }
        SINE_TABLE_MIDPOINT = Mth.SIN[Mth.SIN.length / 2];

        for (int i = 0; i < Mth.SIN.length; i++) {
            float expected = Mth.SIN[i];
            float value = lookup(i);
            if (expected != value) {
                throw new IllegalStateException(String.format("LUT mismatch at %d: expected %s, found %s", i, expected, value));
            }
        }
    }

    public static void init() {}

    public static float sin(double d) {
        return lookup((int) (d * 10430.378350470453) & 0xFFFF);
    }

    public static float cos(double d) {
        return lookup((int) (d * 10430.378350470453 + 16384.0) & 0xFFFF);
    }

    private static float lookup(int index) {
        if (index == 32768) {
            return SINE_TABLE_MIDPOINT;
        }

        int neg = (index & 0x8000) << 16;
        int mask = (index << 17) >> 31;
        int pos = (0x8001 & mask) + (index ^ mask);
        pos &= 0x7fff;

        return Float.intBitsToFloat(SINE_TABLE_INT[pos] ^ neg);
    }
}
