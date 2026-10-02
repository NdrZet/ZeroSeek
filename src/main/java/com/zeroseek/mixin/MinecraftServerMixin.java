package com.zeroseek.mixin;

import com.zeroseek.ZeroSeekMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    /**
     * Prevents the vanilla pause-when-empty deadlock during player handshake,
     * login, or configuration stages. In vanilla 1.21.11, pause-when-empty only
     * checks playerList.getPlayerCount(), which is 0 until the PLAY stage. If the server
     * pauses, chunk loading for spawn freezes, preventing the player from ever joining.
     */
    @Redirect(
        method = "tickServer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;getPlayerCount()I"
        )
    )
    private int zeroseek$guardPlayerCountForEmptyPause(PlayerList playerList) {
        int count = playerList.getPlayerCount();
        if (count > 0) {
            return count;
        }
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (server.getConnection() != null && !server.getConnection().getConnections().isEmpty()) {
            return 1;
        }
        return 0;
    }
}
