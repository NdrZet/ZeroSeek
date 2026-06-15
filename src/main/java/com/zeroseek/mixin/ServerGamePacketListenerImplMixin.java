package com.zeroseek.mixin;

import com.zeroseek.tps.MovementThrottler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void zeroseek$onMovePlayer(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (MovementThrottler.shouldDropMovePacket()) {
            ci.cancel();
        }
    }
}
