package com.zeroseek.tps;

import com.zeroseek.ZeroSeekMod;

import java.util.concurrent.ThreadLocalRandom;

public class AiThrottler {
    public static boolean shouldSkipAi() {
        if (!ZeroSeekMod.CONFIG.tpsGovernorEnabled) {
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
