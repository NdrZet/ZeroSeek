package com.zeroseek.mixin;

import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public class LevelChunkMixin {
    @Unique
    private long zeroseek$loadedAt = System.currentTimeMillis();

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;)V", at = @At("RETURN"))
    private void zeroseek$init(CallbackInfo ci) {
        zeroseek$loadedAt = System.currentTimeMillis();
    }

    @Unique
    public long zeroseek$getLoadedAt() {
        return zeroseek$loadedAt;
    }
}
