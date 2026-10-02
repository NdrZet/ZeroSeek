package com.zeroseek.mixin.worldgen;

import com.zeroseek.ZeroSeekMod;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.concurrent.Executor;

/**
 * Worldgen pipeline interceptor for ChunkMap (ZeroSeek Phase 4).
 * Redirects the worldgen task dispatcher's background executor to ZeroSeek's
 * dedicated, affinity-bound generator worker pool.
 */
@Mixin(ChunkMap.class)
public class ChunkMapMixin {

    @ModifyArg(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkTaskDispatcher;<init>(Lnet/minecraft/util/thread/TaskScheduler;Ljava/util/concurrent/Executor;)V",
            ordinal = 0
        ),
        index = 1
    )
    private Executor zeroseek$redirectWorldgenDispatcherExecutor(Executor executor) {
        if (ZeroSeekMod.CONFIG != null && ZeroSeekMod.CONFIG.concurrentWorldGenEnabled && ZeroSeekMod.ASYNC_SERVICE != null) {
            return ZeroSeekMod.ASYNC_SERVICE.getGeneratorPool().getExecutor();
        }
        return executor;
    }
}
