package com.zeroseek.tps;

import com.zeroseek.ZeroSeekMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;

public class AdaptiveSimulation {
    private static int baseline = -1;
    private static int lastApplied = -1;

    public static void initialize(MinecraftServer server) {
        if (!ZeroSeekMod.CONFIG.tpsGovernorEnabled || !ZeroSeekMod.CONFIG.adaptiveSimulationEnabled) {
            return;
        }
        if (ZeroSeekMod.CONFIG.simDistNormal >= 0) {
            baseline = ZeroSeekMod.CONFIG.simDistNormal;
        } else {
            baseline = server.getPlayerList().getSimulationDistance();
        }
        lastApplied = baseline;
        ZeroSeekMod.LOGGER.info("[ZeroSeek TPS] Adaptive simulation baseline={}", baseline);
    }

    public static void apply(ServerChunkCache cache) {
        if (!ZeroSeekMod.CONFIG.tpsGovernorEnabled || !ZeroSeekMod.CONFIG.adaptiveSimulationEnabled) {
            return;
        }
        if (baseline < 0) {
            return;
        }
        int target = targetDistance();
        if (target != lastApplied) {
            cache.setSimulationDistance(target);
            lastApplied = target;
            ZeroSeekMod.LOGGER.info("[ZeroSeek TPS] Simulation distance changed to {} (state {})",
                    target, ZeroSeekMod.TPS_MONITOR.getState());
        }
    }

    private static int targetDistance() {
        return switch (ZeroSeekMod.TPS_MONITOR.getState()) {
            case NORMAL -> baseline;
            case STRESS -> Math.min(baseline, ZeroSeekMod.CONFIG.simDistStress);
            case CRITICAL -> Math.min(baseline, ZeroSeekMod.CONFIG.simDistCritical);
        };
    }
}
