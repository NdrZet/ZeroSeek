package com.zeroseek.mixin;

import com.zeroseek.ZeroSeekMod;
import net.minecraft.TracingExecutor;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * ChunkMap mixin for ZeroSeek Phase 3 — Async Workers integration.
 * Replaces vanilla background executor with HardenedWorkerPool,
 * adds deduplication cache, and enables prefetch pipeline.
 */
@Mixin(ChunkMap.class)
public class ChunkMapMixin {

    /**
     * Deduplication: if a chunk is already being loaded, return the existing future.
     */
    @Inject(method = "scheduleChunkLoad", at = @At("HEAD"), cancellable = true)
    private void zeroseek$checkLoadingCache(ChunkPos pos, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (ZeroSeekMod.ASYNC_SERVICE == null) return;
        CompletableFuture<ChunkAccess> cached = ZeroSeekMod.ASYNC_SERVICE.getLoadingFuture(pos);
        if (cached != null) {
            if (ZeroSeekMod.CONFIG.debugMmap) {
                ZeroSeekMod.LOGGER.debug("Chunk {} load deduplicated (cache hit)", pos);
            }
            cir.setReturnValue(cached);
        }
    }

    /**
     * Track the returned future in the deduplication cache.
     */
    @Inject(method = "scheduleChunkLoad", at = @At("RETURN"))
    private void zeroseek$trackLoadingFuture(ChunkPos pos, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (ZeroSeekMod.ASYNC_SERVICE == null) return;
        CompletableFuture<ChunkAccess> future = cir.getReturnValue();
        if (future != null) {
            ZeroSeekMod.ASYNC_SERVICE.trackLoadingFuture(pos, future);
        }
    }

    /**
     * Redirect vanilla background executor to ZeroSeek's dedicated parser pool.
     * This offloads NBT parse + DataFixerUpper from Util.ioPool() to our hardened pool.
     */
    @Redirect(
            method = "scheduleChunkLoad",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Util;backgroundExecutor()Lnet/minecraft/TracingExecutor;"
            )
    )
    private TracingExecutor zeroseek$redirectBackgroundExecutor() {
        if (ZeroSeekMod.ASYNC_SERVICE == null) {
            return Util.backgroundExecutor();
        }
        return new TracingExecutor(
                ZeroSeekMod.ASYNC_SERVICE.getParserPool().getExecutorService()
        );
    }
}
