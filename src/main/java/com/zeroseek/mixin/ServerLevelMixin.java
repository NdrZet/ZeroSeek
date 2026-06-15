package com.zeroseek.mixin;

import com.zeroseek.ZeroSeekMod;
import com.zeroseek.tps.EntityHibernation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {

    /**
     * Hibernate entities in chunks that are old enough and under TPS stress.
     * Block ticks continue to run; only entity ticks are skipped.
     */
    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void zeroseek$onTickNonPassenger(Entity entity, CallbackInfo ci) {
        if (!ZeroSeekMod.CONFIG.tpsGovernorEnabled || !ZeroSeekMod.CONFIG.entityHibernationEnabled) {
            return;
        }
        ChunkPos pos = entity.chunkPosition();
        ChunkAccess chunk = ((ServerLevel) (Object) this).getChunk(pos.x, pos.z);
        if (chunk instanceof LevelChunk levelChunk && EntityHibernation.shouldHibernate(levelChunk)) {
            ci.cancel();
        }
    }

    @Inject(method = "unload", at = @At("HEAD"))
    private void zeroseek$onUnload(LevelChunk chunk, CallbackInfo ci) {
        EntityHibernation.unload(chunk);
    }
}
