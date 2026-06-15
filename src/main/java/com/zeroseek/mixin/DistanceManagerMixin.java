package com.zeroseek.mixin;

import com.zeroseek.tps.ChunkTicketGovernor;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DistanceManager.class)
public class DistanceManagerMixin {

    @Inject(method = "addPlayer", at = @At("HEAD"), cancellable = true)
    private void zeroseek$onAddPlayer(SectionPos pos, ServerPlayer player, CallbackInfo ci) {
        if (ChunkTicketGovernor.shouldBlockNewPlayerTickets()) {
            ci.cancel();
        }
    }

    @Inject(method = "updatePlayerTickets", at = @At("HEAD"), cancellable = true)
    private void zeroseek$onUpdatePlayerTickets(int viewDistance, CallbackInfo ci) {
        if (ChunkTicketGovernor.shouldFreezeUpdates()) {
            ci.cancel();
        }
    }
}
