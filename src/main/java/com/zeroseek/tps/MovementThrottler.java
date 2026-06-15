package com.zeroseek.tps;

import com.zeroseek.ZeroSeekMod;

import java.util.concurrent.ThreadLocalRandom;

public class MovementThrottler {
    public static boolean shouldDropMovePacket() {
        if (!ZeroSeekMod.CONFIG.tpsGovernorEnabled) {
            return false;
        }
        double chance = switch (ZeroSeekMod.TPS_MONITOR.getState()) {
            case NORMAL -> 0.0;
            case STRESS -> ZeroSeekMod.CONFIG.stressDropMoveChance;
            case CRITICAL -> ZeroSeekMod.CONFIG.criticalDropMoveChance;
        };
        return chance > 0.0 && ThreadLocalRandom.current().nextDouble() < chance;
    }
}
