package com.zeroseek.tps;

import com.zeroseek.ZeroSeekMod;
import net.minecraft.world.entity.Mob;

import java.util.concurrent.ThreadLocalRandom;

public class AiThrottler {
    public static boolean shouldSkipAi(Mob mob) {
        if (!ZeroSeekMod.CONFIG.tpsGovernorEnabled) {
            return false;
        }
        if (mob == null || mob.level() == null || mob.level().hasNearbyAlivePlayer(mob.getX(), mob.getY(), mob.getZ(), 48.0)) {
            return false;
        }
        double chance = switch (ZeroSeekMod.TPS_MONITOR.getState()) {
            case NORMAL -> 0.0;
            case STRESS -> ZeroSeekMod.CONFIG.stressSkipAiChance;
            case CRITICAL -> ZeroSeekMod.CONFIG.criticalSkipAiChance;
        };
        return chance > 0.0 && ThreadLocalRandom.current().nextDouble() < chance;
    }
}
