package com.zeroseek.mixin.worldgen;

import com.zeroseek.ZeroSeekMod;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.concurrent.Executor;

/**
 * Worldgen pipeline interceptor for ChunkMap (ZeroSeek Phase 4).
 * Redirects the worldgen and light task dispatchers and consecutive executors
 * to ZeroSeek's dedicated, affinity-bound generator worker pool.
 */
@Mixin(ChunkMap.class)
public class ChunkMapMixin {

    @ModifyArg(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkTaskDispatcher;<init>(Lnet/minecraft/util/thread/TaskScheduler;Ljava/util/concurrent/Executor;)V"
        ),
        index = 1
    )
    private Executor zeroseek$redirectDispatcherExecutor(Executor executor) {
        if (ZeroSeekMod.CONFIG != null && ZeroSeekMod.CONFIG.concurrentWorldGenEnabled && ZeroSeekMod.ASYNC_SERVICE != null) {
            return ZeroSeekMod.ASYNC_SERVICE.getGeneratorPool().getExecutor();
        }
        return executor;
    }

    @ModifyArg(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/thread/ConsecutiveExecutor;<init>(Ljava/util/concurrent/Executor;Ljava/lang/String;)V"
        ),
        index = 0
    )
    private Executor zeroseek$redirectConsecutiveExecutor(Executor executor) {
        if (ZeroSeekMod.CONFIG != null && ZeroSeekMod.CONFIG.concurrentWorldGenEnabled && ZeroSeekMod.ASYNC_SERVICE != null) {
            return ZeroSeekMod.ASYNC_SERVICE.getGeneratorPool().getExecutor();
        }
        return executor;
    }
}
