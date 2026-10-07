package com.zeroseek.mixin.worldgen.biome;

import com.zeroseek.worldgen.biome.ClimateColumnCache;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * High-performance 2D column memoization for Climate.Sampler.
 * Eliminates redundant multi-octave 2D noise evaluations for temperature, humidity,
 * continentalness, erosion, and weirdness across all vertical Y-levels within a chunk column.
 */
@Mixin(Climate.Sampler.class)
public abstract class ClimateSamplerMixin {

    @Shadow @Final private DensityFunction temperature;
    @Shadow @Final private DensityFunction humidity;
    @Shadow @Final private DensityFunction continentalness;
    @Shadow @Final private DensityFunction erosion;
    @Shadow @Final private DensityFunction depth;
    @Shadow @Final private DensityFunction weirdness;

    @Inject(method = "sample", at = @At("HEAD"), cancellable = true)
    private void zeroseek$fastSample(int x, int y, int z, CallbackInfoReturnable<Climate.TargetPoint> cir) {
        int blockX = QuartPos.toBlock(x);
        int blockY = QuartPos.toBlock(y);
        int blockZ = QuartPos.toBlock(z);

        ClimateColumnCache.CacheEntry cache = ClimateColumnCache.getThreadCache();
        long colKey = ClimateColumnCache.makeColumnKey(blockX, blockZ);
        int colIdx = ClimateColumnCache.getColumnIndex(colKey);

        float temp, hum, cont, ero, weird;

        if (cache.samplers[colIdx] == this && cache.colKeys[colIdx] == colKey) {
            temp = cache.temp[colIdx];
            hum = cache.hum[colIdx];
            cont = cache.cont[colIdx];
            ero = cache.ero[colIdx];
            weird = cache.weird[colIdx];
        } else {
            ClimateColumnCache.ReusableContext ctx = cache.context;
            ctx.set(blockX, 0, blockZ);

            temp = (float) this.temperature.compute(ctx);
            hum = (float) this.humidity.compute(ctx);
            cont = (float) this.continentalness.compute(ctx);
            ero = (float) this.erosion.compute(ctx);
            weird = (float) this.weirdness.compute(ctx);

            cache.samplers[colIdx] = this;
            cache.colKeys[colIdx] = colKey;
            cache.temp[colIdx] = temp;
            cache.hum[colIdx] = hum;
            cache.cont[colIdx] = cont;
            cache.ero[colIdx] = ero;
            cache.weird[colIdx] = weird;
        }

        ClimateColumnCache.ReusableContext ctx = cache.context;
        ctx.set(blockX, blockY, blockZ);
        float dep = (float) this.depth.compute(ctx);

        cir.setReturnValue(Climate.target(temp, hum, cont, ero, dep, weird));
    }
}
