package com.zeroseek.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zeroseek.tps.AiThrottler;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Mob.class)
public abstract class MobMixin {

    @WrapOperation(
            method = "serverAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tick()V"
            )
    )
    private void zeroseek$wrapGoalSelectorTick(GoalSelector instance, Operation<Void> original) {
        if (AiThrottler.shouldSkipAi((Mob) (Object) this)) {
            return;
        }
        original.call(instance);
    }

    @WrapOperation(
            method = "serverAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tickRunningGoals(Z)V"
            )
    )
    private void zeroseek$wrapGoalSelectorTickRunningGoals(GoalSelector instance, boolean pause, Operation<Void> original) {
        if (AiThrottler.shouldSkipAi((Mob) (Object) this)) {
            return;
        }
        original.call(instance, pause);
    }
}
