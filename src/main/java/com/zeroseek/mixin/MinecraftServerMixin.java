package com.zeroseek.mixin;

import com.zeroseek.ZeroSeekMod;
import com.zeroseek.tps.AdaptiveSimulation;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void zeroseek$onTick(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        if (ZeroSeekMod.TPS_MONITOR == null) {
            return;
        }
        ZeroSeekMod.TPS_MONITOR.onTick((MinecraftServer) (Object) this);
    }

    @Inject(method = "runServer", at = @At("HEAD"))
    private void zeroseek$onRunServer(CallbackInfo ci) {
        AdaptiveSimulation.initialize((MinecraftServer) (Object) this);
    }
}
