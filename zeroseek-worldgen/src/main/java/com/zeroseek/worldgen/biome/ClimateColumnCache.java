package com.zeroseek.worldgen.biome;

import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.Arrays;

/**
 * High-performance 2D column memoization cache for horizontal climate parameters.
 * Eliminates redundant 2D noise evaluations across all 96 vertical Y-quartiles in a chunk column.
 * Memory allocated in ThreadLocal buffers to satisfy zero-contention concurrency invariants.
 */
public final class ClimateColumnCache {

    public static final int CACHE_SIZE = 1024;
    public static final int CACHE_MASK = CACHE_SIZE - 1;

    public static final class ReusableContext implements DensityFunction.FunctionContext {
        public int x;
        public int y;
        public int z;

        public void set(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int blockX() {
            return this.x;
        }

        @Override
        public int blockY() {
            return this.y;
        }

        @Override
        public int blockZ() {
            return this.z;
        }
    }

    public static final class CacheEntry {
        public final Object[] samplers = new Object[CACHE_SIZE];
        public final long[] colKeys = new long[CACHE_SIZE];
        public final float[] temp = new float[CACHE_SIZE];
        public final float[] hum = new float[CACHE_SIZE];
        public final float[] cont = new float[CACHE_SIZE];
        public final float[] ero = new float[CACHE_SIZE];
        public final float[] weird = new float[CACHE_SIZE];
        public final ReusableContext context = new ReusableContext();

        public CacheEntry() {
            Arrays.fill(this.colKeys, Long.MIN_VALUE);
        }
    }

    private static final ThreadLocal<CacheEntry> THREAD_CACHE =
            ThreadLocal.withInitial(CacheEntry::new);

    public static CacheEntry getThreadCache() {
        return THREAD_CACHE.get();
    }

    public static long makeColumnKey(int blockX, int blockZ) {
        return (((long) blockX) << 32) | (((long) blockZ) & 0xFFFFFFFFL);
    }

    public static int getColumnIndex(long colKey) {
        return (int) (colKey ^ (colKey >>> 16) ^ (colKey >>> 32)) & CACHE_MASK;
    }
}
