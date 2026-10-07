package com.zeroseek.mixin.worldgen;

import com.zeroseek.ZeroSeekMod;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.concurrent.Executor;

/**
 * Eliminates nested executor bouncing in NoiseBasedChunkGenerator (ZeroSeek Phase 4).
 */
@Mixin(NoiseBasedChunkGenerator.class)
public class NoiseBasedChunkGeneratorMixin {

    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private net.minecraft.core.Holder<net.minecraft.world.level.levelgen.NoiseGeneratorSettings> settings;
    private int zeroseek$cachedSeaLevel = Integer.MIN_VALUE;

    /**
     * @author ZeroSeek
     * @reason Cache seaLevel to avoid constant registry lookup during chunk generation
     */
    @org.spongepowered.asm.mixin.Overwrite
    public int getSeaLevel() {
        if (this.zeroseek$cachedSeaLevel == Integer.MIN_VALUE) {
            this.zeroseek$cachedSeaLevel = this.settings.value().seaLevel();
        }
        return this.zeroseek$cachedSeaLevel;
    }


    @ModifyArg(
        method = "fillFromNoise",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private Executor zeroseek$redirectFillFromNoiseExecutor(Executor executor) {
        if (ZeroSeekMod.CONFIG != null && ZeroSeekMod.CONFIG.concurrentWorldGenEnabled && ZeroSeekMod.ASYNC_SERVICE != null) {
            return ZeroSeekMod.ASYNC_SERVICE.getGeneratorPool().getExecutor();
        }
        return executor;
    }

    @ModifyArg(
        method = "createBiomes",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private Executor zeroseek$redirectCreateBiomesExecutor(Executor executor) {
        if (ZeroSeekMod.CONFIG != null && ZeroSeekMod.CONFIG.concurrentWorldGenEnabled && ZeroSeekMod.ASYNC_SERVICE != null) {
            return ZeroSeekMod.ASYNC_SERVICE.getGeneratorPool().getExecutor();
        }
        return executor;
    }
}

