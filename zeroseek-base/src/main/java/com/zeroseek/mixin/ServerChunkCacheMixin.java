package com.zeroseek.mixin;

import com.zeroseek.tps.AdaptiveSimulation;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(ServerChunkCache.class)
public class ServerChunkCacheMixin {

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;Z)V", at = @At("HEAD"))
    private void zeroseek$onTick(BooleanSupplier hasTimeLeft, boolean hasTimeLeft2, CallbackInfo ci) {
        AdaptiveSimulation.apply((ServerChunkCache) (Object) this);
    }
}
