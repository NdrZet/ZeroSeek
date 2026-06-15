package com.zeroseek.chunk;

import com.zeroseek.ZeroSeekMod;
import com.zeroseek.async.AsyncChunkService;
import com.zeroseek.mixin.ChunkMapInvoker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Conservative predictive chunk prefetcher based on player movement vectors.
 * Designed to stay out of the way: small radius, speed threshold, tick throttle,
 * and a hard per-tick cap to avoid overwhelming the chunk loader.
 */
public class ChunkPrefetcher {
    private static final AtomicInteger tickCounter = new AtomicInteger(0);
    private static final int DEFAULT_TICK_INTERVAL = 5;
    private static final double DEFAULT_SPEED_THRESHOLD = 0.15;
    private static final int DEFAULT_MAX_PER_TICK = 16;

    /**
     * Called from server tick event for each level.
     */
    public static void onTick(ServerLevel level) {
        if (!ZeroSeekMod.CONFIG.chunkPrefetchEnabled) return;
        if (ZeroSeekMod.ASYNC_SERVICE == null) return;

        int tick = tickCounter.incrementAndGet();
        int interval = Math.max(1, ZeroSeekMod.CONFIG.chunkPrefetchTickInterval);
        if (tick % interval != 0) return;

        AsyncChunkService service = ZeroSeekMod.ASYNC_SERVICE;
        var chunkMap = level.getChunkSource().chunkMap;
        int radius = Math.max(0, Math.min(2, ZeroSeekMod.CONFIG.chunkPrefetchRadius));
        int ticksAhead = Math.max(5, ZeroSeekMod.CONFIG.chunkPrefetchTicksAhead);
        double speedThreshold = Math.max(0.0, ZeroSeekMod.CONFIG.chunkPrefetchSpeedThreshold);
        int maxPerTick = Math.max(0, ZeroSeekMod.CONFIG.chunkPrefetchMaxPerTick);

        int scheduledThisTick = 0;

        for (ServerPlayer player : level.players()) {
            ChunkPos current = player.chunkPosition();
            Vec3 velocity = player.getDeltaMovement();
            double speedXZ = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

            // Skip stationary/slow players
            if (speedXZ < speedThreshold) continue;

            // Predict chunk position ahead, clamp to sane bounds
            int predictX = (int) Math.round(velocity.x * ticksAhead / 16.0);
            int predictZ = (int) Math.round(velocity.z * ticksAhead / 16.0);
            if (Math.abs(predictX) > 8) predictX = Integer.signum(predictX) * 8;
            if (Math.abs(predictZ) > 8) predictZ = Integer.signum(predictZ) * 8;

            // Skip if prediction is zero movement
            if (predictX == 0 && predictZ == 0 && radius == 0) continue;

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (scheduledThisTick >= maxPerTick) return;

                    ChunkPos target = new ChunkPos(current.x + predictX + dx, current.z + predictZ + dz);

                    if (service.isCachedOrLoading(target)) continue;

                    try {
                        CompletableFuture<ChunkAccess> future =
                                ((ChunkMapInvoker) chunkMap).zeroseek$invokeScheduleChunkLoad(target);
                        service.trackLoadingFuture(target, future);
                        scheduledThisTick++;

                        if (ZeroSeekMod.CONFIG.debugMmap) {
                            ZeroSeekMod.LOGGER.debug(
                                    "Prefetch chunk {} for player {} (vel={:.2f},{:.2f})",
                                    target, player.getScoreboardName(),
                                    velocity.x, velocity.z
                            );
                        }
                    } catch (Exception e) {
                        ZeroSeekMod.LOGGER.error("Prefetch failed for chunk {}", target, e);
                    }
                }
            }
        }
    }
}
