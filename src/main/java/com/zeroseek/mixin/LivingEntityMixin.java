package com.zeroseek.mixin;

import com.zeroseek.tps.AiThrottler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void zeroseek$onAiStep(CallbackInfo ci) {
        if (((Object) this) instanceof Player) {
            return;
        }
        if (AiThrottler.shouldSkipAi()) {
            ci.cancel();
        }
    }
}
