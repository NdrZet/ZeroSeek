package com.zeroseek.mixin.worldgen;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 2D coordinate cache for TheEndBiomeSource (ZeroSeek Phase 5).
 * End biomes are 2D-projected and depend strictly on (x, z), ignoring y.
 * Caches sampled biomes in a ThreadLocal LRU map to avoid repeated erosion noise evaluations.
 */
@Mixin(TheEndBiomeSource.class)
public class TheEndBiomeSourceMixin {

    @Unique
    private static final int ZEROSEEK$CACHE_CAPACITY = 1024;

    @Unique
    private final ThreadLocal<Long2ObjectLinkedOpenHashMap<Holder<Biome>>> zeroseek$cache =
            ThreadLocal.withInitial(() -> new Long2ObjectLinkedOpenHashMap<>(ZEROSEEK$CACHE_CAPACITY));

    @Inject(method = "getNoiseBiome", at = @At("HEAD"), cancellable = true)
    private void zeroseek$getCachedEndBiome(int x, int y, int z, Climate.Sampler sampler, CallbackInfoReturnable<Holder<Biome>> cir) {
        long key = ChunkPos.asLong(x, z);
        Long2ObjectLinkedOpenHashMap<Holder<Biome>> cache = zeroseek$cache.get();
        Holder<Biome> cached = cache.get(key);
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "getNoiseBiome", at = @At("RETURN"))
    private void zeroseek$putCachedEndBiome(int x, int y, int z, Climate.Sampler sampler, CallbackInfoReturnable<Holder<Biome>> cir) {
        Holder<Biome> result = cir.getReturnValue();
        if (result == null) return;
        long key = ChunkPos.asLong(x, z);
        Long2ObjectLinkedOpenHashMap<Holder<Biome>> cache = zeroseek$cache.get();
        cache.put(key, result);
        if (cache.size() > ZEROSEEK$CACHE_CAPACITY) {
            for (int i = 0; i < 64; i++) {
                cache.removeFirst();
            }
        }
    }
}
