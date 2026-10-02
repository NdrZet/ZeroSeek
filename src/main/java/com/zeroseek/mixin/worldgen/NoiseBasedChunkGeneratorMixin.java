package com.zeroseek.mixin.worldgen;

import com.zeroseek.ZeroSeekMod;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.concurrent.Executor;

/**
 * Eliminates nested executor bouncing in NoiseBasedChunkGenerator (ZeroSeek Phase 4).
 * Redirects supplyAsync dispatch in fillFromNoise and createBiomes to Runnable::run,
 * executing them directly on the dedicated generator worker thread already running the step.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public class NoiseBasedChunkGeneratorMixin {

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
