package com.zeroseek.tps;

import com.zeroseek.ZeroSeekMod;
import net.minecraft.server.MinecraftServer;

public class TPSMonitor {
    private volatile TPSState state = TPSState.NORMAL;

    public void onTick(MinecraftServer server) {
        if (!ZeroSeekMod.CONFIG.tpsGovernorEnabled) {
            state = TPSState.NORMAL;
            return;
        }
        long avgNanos = server.getAverageTickTimeNanos();
        double tps = avgNanos > 0 ? Math.min(20.0, 1_000_000_000.0 / avgNanos) : 20.0;
        state = classify(tps);
        TickAggregator.maybeLog(tps, state);
    }

    private TPSState classify(double tps) {
        if (tps <= ZeroSeekMod.CONFIG.tpsCriticalThreshold) {
            return TPSState.CRITICAL;
        }
        if (tps <= ZeroSeekMod.CONFIG.tpsStressThreshold) {
            return TPSState.STRESS;
        }
        return TPSState.NORMAL;
    }

    public TPSState getState() {
        return state;
    }
}
