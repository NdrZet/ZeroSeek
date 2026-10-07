package com.zeroseek.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.concurrent.CompletableFuture;

/**
 * Invoker to access ChunkMap.scheduleChunkLoad for prefetch purposes.
 */
@Mixin(ChunkMap.class)
public interface ChunkMapInvoker {
    @Invoker("scheduleChunkLoad")
    CompletableFuture<ChunkAccess> zeroseek$invokeScheduleChunkLoad(ChunkPos pos);
}
