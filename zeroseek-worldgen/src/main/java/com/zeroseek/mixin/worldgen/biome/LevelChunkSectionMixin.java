package com.zeroseek.mixin.worldgen.biome;

import com.zeroseek.ZeroSeekMod;
import com.zeroseek.mixin.worldgen.palette.PalettedContainerAccessor;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.Strategy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * High-performance adaptive biome section culling.
 * Eliminates redundant 3D noise evaluations when chunk sections are uniform.
 */
@Mixin(LevelChunkSection.class)
public abstract class LevelChunkSectionMixin {

    @Shadow
    private PalettedContainerRO<Holder<Biome>> biomes;

    @Inject(method = "fillBiomesFromNoise", at = @At("HEAD"), cancellable = true)
    private void zeroseek$fastFillBiomesFromNoise(BiomeResolver biomeResolver, Climate.Sampler sampler, int minQuartX, int minQuartY, int minQuartZ, CallbackInfo ci) {
        if (ZeroSeekMod.CONFIG == null || !ZeroSeekMod.CONFIG.biomeOptimizationEnabled) {
            return;
        }

        // Stratosphere section fast check (Y >= 384, quartY >= 96):
        // In stratosphere, horizontal climate variations are smooth and vertical variations are 0.
        // Checking 4 horizontal corners is sufficient.
        if (minQuartY >= 96) {
            Holder<Biome> b00 = biomeResolver.getNoiseBiome(minQuartX, minQuartY, minQuartZ, sampler);
            Holder<Biome> b33 = biomeResolver.getNoiseBiome(minQuartX + 3, minQuartY, minQuartZ + 3, sampler);
            if (b00 == b33) {
                Holder<Biome> b30 = biomeResolver.getNoiseBiome(minQuartX + 3, minQuartY, minQuartZ, sampler);
                Holder<Biome> b03 = biomeResolver.getNoiseBiome(minQuartX, minQuartY, minQuartZ + 3, sampler);
                if (b00 == b30 && b00 == b03) {
                    if (zeroseek$applySingleBiome(b00)) {
                        ci.cancel();
                        return;
                    }
                }
            }
        }

        // General 3D Section Fast-Path:
        // 1. Check opposite 3D diagonal corners first
        Holder<Biome> b0 = biomeResolver.getNoiseBiome(minQuartX, minQuartY, minQuartZ, sampler);
        Holder<Biome> b7 = biomeResolver.getNoiseBiome(minQuartX + 3, minQuartY + 3, minQuartZ + 3, sampler);

        if (b0 != b7) {
            // Diagonal mismatch: definitely heterogeneous, fall back to vanilla loop
            return;
        }

        // 2. Check remaining 6 corners of the 4x4x4 cube
        Holder<Biome> b1 = biomeResolver.getNoiseBiome(minQuartX + 3, minQuartY, minQuartZ, sampler);
        if (b1 != b0) return;

        Holder<Biome> b2 = biomeResolver.getNoiseBiome(minQuartX, minQuartY + 3, minQuartZ, sampler);
        if (b2 != b0) return;

        Holder<Biome> b3 = biomeResolver.getNoiseBiome(minQuartX + 3, minQuartY + 3, minQuartZ, sampler);
        if (b3 != b0) return;

        Holder<Biome> b4 = biomeResolver.getNoiseBiome(minQuartX, minQuartY, minQuartZ + 3, sampler);
        if (b4 != b0) return;

        Holder<Biome> b5 = biomeResolver.getNoiseBiome(minQuartX + 3, minQuartY, minQuartZ + 3, sampler);
        if (b5 != b0) return;

        Holder<Biome> b6 = biomeResolver.getNoiseBiome(minQuartX, minQuartY + 3, minQuartZ + 3, sampler);
        if (b6 != b0) return;

        // 3. Check 2 interior center points to guarantee no local saddle points
        Holder<Biome> c1 = biomeResolver.getNoiseBiome(minQuartX + 1, minQuartY + 1, minQuartZ + 1, sampler);
        if (c1 != b0) return;

        Holder<Biome> c2 = biomeResolver.getNoiseBiome(minQuartX + 2, minQuartY + 2, minQuartZ + 2, sampler);
        if (c2 != b0) return;

        // All 8 corners and interior points match: 100% uniform section!
        if (zeroseek$applySingleBiome(b0)) {
            ci.cancel();
        }
    }

    @Unique
    @SuppressWarnings("unchecked")
    private boolean zeroseek$applySingleBiome(Holder<Biome> biome) {
        if (this.biomes instanceof PalettedContainerAccessor<?> accessor) {
            Strategy<Holder<Biome>> strategy = (Strategy<Holder<Biome>>) accessor.zeroseek$getStrategy();
            if (strategy != null) {
                this.biomes = new PalettedContainer<>(biome, strategy);
                return true;
            }
        }
        return false;
    }
}
