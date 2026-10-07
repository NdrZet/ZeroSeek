package com.zeroseek.worldgen.biome;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

import java.util.Arrays;

/**
 * Fast ThreadLocal direct-mapped lookup cache for MultiNoise search evaluations.
 * Replaces expensive 7-dimensional R-tree traversals with O(1) hash hits for identical climate points.
 */
public final class SearchTreeCache {

    public static final int CACHE_SIZE = 2048;
    public static final int CACHE_MASK = CACHE_SIZE - 1;

    private static final class ThreadCache {
        final Object[] sources = new Object[CACHE_SIZE];
        final Climate.TargetPoint[] targets = new Climate.TargetPoint[CACHE_SIZE];
        @SuppressWarnings("unchecked")
        final Holder<Biome>[] biomes = new Holder[CACHE_SIZE];

        ThreadCache() {
            Arrays.fill(this.targets, null);
        }
    }

    private static final ThreadLocal<ThreadCache> THREAD_CACHE =
            ThreadLocal.withInitial(ThreadCache::new);

    public static Holder<Biome> get(Object source, Climate.TargetPoint target) {
        if (target == null) return null;
        ThreadCache cache = THREAD_CACHE.get();
        int idx = (int) (target.hashCode() ^ (target.hashCode() >>> 16)) & CACHE_MASK;
        if (cache.sources[idx] == source) {
            Climate.TargetPoint cachedTarget = cache.targets[idx];
            if (cachedTarget != null && cachedTarget.equals(target)) {
                return cache.biomes[idx];
            }
        }
        return null;
    }

    public static void put(Object source, Climate.TargetPoint target, Holder<Biome> biome) {
        if (target == null || biome == null) return;
        ThreadCache cache = THREAD_CACHE.get();
        int idx = (int) (target.hashCode() ^ (target.hashCode() >>> 16)) & CACHE_MASK;
        cache.sources[idx] = source;
        cache.targets[idx] = target;
        cache.biomes[idx] = biome;
    }
}
