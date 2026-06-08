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

/**
 * Predictive chunk prefetcher based on player movement vectors.
 * Runs every server tick and schedules async loads for chunks ahead of players.
 */
public class ChunkPrefetcher {

    /**
     * Called from server tick event for each level.
     */
    public static void onTick(ServerLevel level) {
        if (!ZeroSeekMod.CONFIG.chunkPrefetchEnabled) return;
        if (ZeroSeekMod.ASYNC_SERVICE == null) return;

        AsyncChunkService service = ZeroSeekMod.ASYNC_SERVICE;
        var chunkMap = level.getChunkSource().chunkMap;
        int radius = ZeroSeekMod.CONFIG.chunkPrefetchRadius;
        int ticksAhead = ZeroSeekMod.CONFIG.chunkPrefetchTicksAhead;

        for (ServerPlayer player : level.players()) {
            ChunkPos current = player.chunkPosition();
            Vec3 velocity = player.getDeltaMovement();

            // Predict chunk position ahead
            int predictX = (int) Math.round(velocity.x * ticksAhead / 16.0);
            int predictZ = (int) Math.round(velocity.z * ticksAhead / 16.0);

            // Clamp prediction to avoid extreme prefetch on teleport/respawn
            if (Math.abs(predictX) > 8) predictX = Integer.signum(predictX) * 8;
            if (Math.abs(predictZ) > 8) predictZ = Integer.signum(predictZ) * 8;

            // Prefetch area around predicted position
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    ChunkPos target = new ChunkPos(current.x + predictX + dx, current.z + predictZ + dz);

                    if (service.isCachedOrLoading(target)) continue;

                    try {
                        CompletableFuture<ChunkAccess> future =
                                ((ChunkMapInvoker) chunkMap).zeroseek$invokeScheduleChunkLoad(target);
                        service.trackLoadingFuture(target, future);

                        if (ZeroSeekMod.CONFIG.debugMmap) {
                            ZeroSeekMod.LOGGER.debug(
                                    "Prefetch chunk {} for player {} (vel={},{})",
                                    target, player.getScoreboardName(),
                                    String.format("%.2f", velocity.x),
                                    String.format("%.2f", velocity.z)
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
