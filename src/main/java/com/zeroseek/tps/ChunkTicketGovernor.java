package com.zeroseek.tps;

import com.zeroseek.ZeroSeekMod;

public class ChunkTicketGovernor {
    public static boolean shouldBlockNewPlayerTickets() {
        if (!ZeroSeekMod.CONFIG.tpsGovernorEnabled) {
            return false;
        }
        TPSState state = ZeroSeekMod.TPS_MONITOR.getState();
        return state == TPSState.STRESS || state == TPSState.CRITICAL;
    }

    public static boolean shouldFreezeUpdates() {
        if (!ZeroSeekMod.CONFIG.tpsGovernorEnabled) {
            return false;
        }
        return ZeroSeekMod.TPS_MONITOR.getState() == TPSState.CRITICAL;
    }
}
