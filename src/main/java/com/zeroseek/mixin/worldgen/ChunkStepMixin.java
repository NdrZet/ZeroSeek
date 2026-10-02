package com.zeroseek.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zeroseek.ZeroSeekMod;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTask;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;

/**
 * Worldgen pipeline interceptor for ChunkStep (ZeroSeek Phase 4).
 * Intercepts ChunkStatusTask.doWork to guard concurrent block writes with SpatialLockManager.
 */
@Mixin(ChunkStep.class)
public class ChunkStepMixin {

    @WrapOperation(
        method = "apply",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/status/ChunkStatusTask;doWork(Lnet/minecraft/world/level/chunk/status/WorldGenContext;Lnet/minecraft/world/level/chunk/status/ChunkStep;Lnet/minecraft/util/StaticCache2D;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private CompletableFuture<ChunkAccess> zeroseek$wrapChunkStatusTaskDoWork(
        ChunkStatusTask instance,
        WorldGenContext context,
        ChunkStep step,
        StaticCache2D<GenerationChunkHolder> cache,
        ChunkAccess chunk,
        Operation<CompletableFuture<ChunkAccess>> original
    ) {
        if (ZeroSeekMod.CONFIG == null || !ZeroSeekMod.CONFIG.concurrentWorldGenEnabled || ZeroSeekMod.SPATIAL_LOCK_MANAGER == null) {
            return original.call(instance, context, step, cache, chunk);
        }
        int radius = step.blockStateWriteRadius();
        if (radius <= 0) {
            return original.call(instance, context, step, cache, chunk);
        }
        return ZeroSeekMod.SPATIAL_LOCK_MANAGER.computeWithLock(
            chunk.getPos(),
            radius,
            () -> original.call(instance, context, step, cache, chunk)
        );
    }
}
