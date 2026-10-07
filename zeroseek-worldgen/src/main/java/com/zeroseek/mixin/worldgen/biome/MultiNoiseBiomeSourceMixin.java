package com.zeroseek.mixin.worldgen.biome;

import com.zeroseek.worldgen.biome.SearchTreeCache;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * High-performance search tree memoization for MultiNoiseBiomeSource.
 * Intercepts getNoiseBiome(TargetPoint) and eliminates redundant 7D R-tree traversals
 * across adjacent vertical quartiles within chunk columns.
 */
@Mixin(MultiNoiseBiomeSource.class)
public abstract class MultiNoiseBiomeSourceMixin {

    @Inject(
            method = "getNoiseBiome(Lnet/minecraft/world/level/biome/Climate$TargetPoint;)Lnet/minecraft/core/Holder;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void zeroseek$getCachedTargetBiome(Climate.TargetPoint targetPoint, CallbackInfoReturnable<Holder<Biome>> cir) {
        Holder<Biome> cached = SearchTreeCache.get(this, targetPoint);
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(
            method = "getNoiseBiome(Lnet/minecraft/world/level/biome/Climate$TargetPoint;)Lnet/minecraft/core/Holder;",
            at = @At("RETURN")
    )
    private void zeroseek$putCachedTargetBiome(Climate.TargetPoint targetPoint, CallbackInfoReturnable<Holder<Biome>> cir) {
        Holder<Biome> val = cir.getReturnValue();
        if (val != null) {
            SearchTreeCache.put(this, targetPoint, val);
        }
    }
}
