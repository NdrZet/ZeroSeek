package com.zeroseek.mixin.worldgen.biome;

import com.zeroseek.ZeroSeekMod;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * High-performance sky biome section culling and uniform propagation for ChunkAccess.
 * In the upper atmosphere (Y >= 384+), biomes have 0 vertical variation; this mixin
 * propagates the sky biome container across all stratosphere sections with 0 noise queries.
 */
@Mixin(ChunkAccess.class)
public abstract class ChunkAccessBiomeMixin {

    @Shadow public abstract ChunkPos getPos();
    @Shadow public abstract LevelHeightAccessor getHeightAccessorForGeneration();
    @Shadow public abstract LevelChunkSection getSection(int index);

    @Inject(method = "fillBiomesFromNoise", at = @At("HEAD"), cancellable = true)
    private void zeroseek$fillBiomesWithSkyCulling(BiomeResolver biomeResolver, Climate.Sampler sampler, CallbackInfo ci) {
        if (ZeroSeekMod.CONFIG == null || !ZeroSeekMod.CONFIG.biomeOptimizationEnabled) {
            return;
        }

        ChunkPos chunkPos = this.getPos();
        int minQuartX = QuartPos.fromBlock(chunkPos.getMinBlockX());
        int minQuartZ = QuartPos.fromBlock(chunkPos.getMinBlockZ());
        LevelHeightAccessor heightAccessor = this.getHeightAccessorForGeneration();
        int minSectionY = heightAccessor.getMinSectionY();
        int maxSectionY = heightAccessor.getMaxSectionY();

        int stratosphereSectionY = ZeroSeekMod.CONFIG.stratosphereBiomeSectionY;
        LevelChunkSection lastSkySection = null;

        for (int secY = minSectionY; secY <= maxSectionY; ++secY) {
            LevelChunkSection section = this.getSection(heightAccessor.getSectionIndexFromSectionY(secY));
            if (section == null) {
                continue;
            }

            if (secY > stratosphereSectionY && lastSkySection != null) {
                PalettedContainerRO<Holder<Biome>> skyBiomes = lastSkySection.getBiomes();
                if (skyBiomes != null) {
                    ((LevelChunkSectionAccessor) section).zeroseek$setBiomes(skyBiomes.copy());
                    continue;
                }
            }

            int minQuartY = QuartPos.fromSection(secY);
            section.fillBiomesFromNoise(biomeResolver, sampler, minQuartX, minQuartY, minQuartZ);

            if (secY >= stratosphereSectionY) {
                lastSkySection = section;
            }
        }

        ci.cancel();
    }
}
