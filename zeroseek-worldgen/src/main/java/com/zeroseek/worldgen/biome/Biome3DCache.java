package com.zeroseek.worldgen.biome;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import java.util.Arrays;

/**
 * 3D spatial cache for MultiNoiseBiomeSource evaluations.
 * Kept outside mixin packages to satisfy SpongePowered Mixin classloader invariants.
 */
public final class Biome3DCache {

    public static final int CACHE_SIZE = 8192;
    public static final int CACHE_MASK = CACHE_SIZE - 1;

    private static final class CacheEntry {
        final Object[] sources = new Object[CACHE_SIZE];
        final long[] keys = new long[CACHE_SIZE];
        @SuppressWarnings("unchecked")
        final Holder<Biome>[] values = new Holder[CACHE_SIZE];

        CacheEntry() {
            Arrays.fill(keys, Long.MIN_VALUE);
        }
    }

    private static final ThreadLocal<CacheEntry> THREAD_CACHE =
            ThreadLocal.withInitial(CacheEntry::new);

    public static long makeKey(int x, int y, int z) {
        return (((long) x & 0xFFFFFFL) << 36) | (((long) (y + 2048) & 0xFFFL) << 24) | ((long) z & 0xFFFFFFL);
    }

    public static int getIndex(long key) {
        return (int) (key ^ (key >>> 16) ^ (key >>> 32)) & CACHE_MASK;
    }

    public static Holder<Biome> get(Object source, long key, int index) {
        CacheEntry cache = THREAD_CACHE.get();
        if (cache.sources[index] == source && cache.keys[index] == key) {
            return cache.values[index];
        }
        return null;
    }

    public static void put(Object source, long key, int index, Holder<Biome> biome) {
        if (biome == null) return;
        CacheEntry cache = THREAD_CACHE.get();
        cache.sources[index] = source;
        cache.keys[index] = key;
        cache.values[index] = biome;
    }
}
