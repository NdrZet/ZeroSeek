package com.zeroseek.mixin;

import com.zeroseek.tps.EntityHibernation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {

    @Inject(method = "tickChunk", at = @At("HEAD"), cancellable = true)
    private void zeroseek$onTickChunk(LevelChunk chunk, int randomTicks, CallbackInfo ci) {
        if (EntityHibernation.shouldHibernate(chunk)) {
            ci.cancel();
        }
    }

    @Inject(method = "unload", at = @At("HEAD"))
    private void zeroseek$onUnload(LevelChunk chunk, CallbackInfo ci) {
        EntityHibernation.unload(chunk);
    }
}
