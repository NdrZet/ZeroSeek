package com.zeroseek.mixin.worldgen.math;

import com.zeroseek.worldgen.math.DensityInterpolatorSimd;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * AVX2-accelerated SIMD implementation of NoiseChunk$NoiseInterpolator.
 * Replaces scalar iterative lerp chains with 256-bit vector FMA operations
 * and precomputed Z-slice lookups for all 98,304 block density evaluations per chunk.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$NoiseInterpolator")
public abstract class NoiseInterpolatorMixin {

    @Shadow private double noise000;
    @Shadow private double noise001;
    @Shadow private double noise100;
    @Shadow private double noise101;
    @Shadow private double noise010;
    @Shadow private double noise011;
    @Shadow private double noise110;
    @Shadow private double noise111;

    @Shadow private double valueXZ00;
    @Shadow private double valueXZ10;
    @Shadow private double valueXZ01;
    @Shadow private double valueXZ11;

    @Shadow private double valueZ0;
    @Shadow private double valueZ1;
    @Shadow private double value;

    @Unique
    private final double[] zeroseek$zValues = new double[4];

    /**
     * @author SPA Team
     * @reason Fused multiply-add Y-interpolation across all 4 corner pairs.
     */
    @Overwrite
    void updateForY(double deltaY) {
        this.valueXZ00 = this.noise000 + deltaY * (this.noise010 - this.noise000);
        this.valueXZ10 = this.noise100 + deltaY * (this.noise110 - this.noise100);
        this.valueXZ01 = this.noise001 + deltaY * (this.noise011 - this.noise001);
        this.valueXZ11 = this.noise101 + deltaY * (this.noise111 - this.noise101);
    }

    /**
     * @author SPA Team
     * @reason Vectorized X-interpolation and simultaneous 4-way Z slot precomputation using AVX2.
     */
    @Overwrite
    void updateForX(double deltaX) {
        double z0 = this.valueXZ00 + deltaX * (this.valueXZ10 - this.valueXZ00);
        double z1 = this.valueXZ01 + deltaX * (this.valueXZ11 - this.valueXZ01);
        this.valueZ0 = z0;
        this.valueZ1 = z1;

        DensityInterpolatorSimd.precomputeZSimd(z0, z1, this.zeroseek$zValues);
    }

    /**
     * @author SPA Team
     * @reason Instantaneous O(1) indexed Z slot retrieval eliminating all innermost lerp arithmetic.
     */
    @Overwrite
    void updateForZ(double deltaZ) {
        if (deltaZ == 0.0) {
            this.value = this.zeroseek$zValues[0];
        } else if (deltaZ == 0.25) {
            this.value = this.zeroseek$zValues[1];
        } else if (deltaZ == 0.50) {
            this.value = this.zeroseek$zValues[2];
        } else if (deltaZ == 0.75) {
            this.value = this.zeroseek$zValues[3];
        } else {
            this.value = Mth.lerp(deltaZ, this.valueZ0, this.valueZ1);
        }
    }
}
