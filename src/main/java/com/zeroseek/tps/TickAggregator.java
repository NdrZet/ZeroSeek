package com.zeroseek.tps;

import com.zeroseek.ZeroSeekMod;

public class TickAggregator {
    private static int tickCounter = 0;
    private static final int LOG_INTERVAL = 100;

    public static void maybeLog(double tps, TPSState state) {
        if (++tickCounter % LOG_INTERVAL != 0) {
            return;
        }
        if (ZeroSeekMod.CONFIG.debugMmap || state != TPSState.NORMAL) {
            ZeroSeekMod.LOGGER.info("[ZeroSeek TPS] TPS={:.1f}, state={}", tps, state);
        }
    }
}
