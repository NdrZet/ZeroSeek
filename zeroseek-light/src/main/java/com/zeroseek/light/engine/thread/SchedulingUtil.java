package com.zeroseek.light.engine.thread;

import com.zeroseek.ZeroSeekMod;
import com.zeroseek.worldgen.ZeroSeekWorldGenMod;
import net.minecraft.world.level.ChunkPos;

import java.util.concurrent.CompletableFuture;

public class SchedulingUtil {

    public static void scheduleTask(int ownerTag, Runnable task, int x, int z, int radius) {
        int boundedRadius = Math.min(1, Math.max(0, radius));
        if (ZeroSeekWorldGenMod.SPATIAL_LOCK_MANAGER != null) {
            ZeroSeekWorldGenMod.SPATIAL_LOCK_MANAGER.computeWithLock(new ChunkPos(x, z), boundedRadius, () -> {
                task.run();
                return CompletableFuture.completedFuture(null);
            });
        } else if (ZeroSeekMod.ASYNC_SERVICE != null) {
            ZeroSeekMod.ASYNC_SERVICE.getGeneratorPool().getExecutor().execute(task);
        } else {
            java.util.concurrent.ForkJoinPool.commonPool().execute(task);
        }
    }

    public static boolean isExternallyManaged() {
        return false;
    }
}
