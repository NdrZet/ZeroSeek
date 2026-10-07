package com.zeroseek.tps;

import com.zeroseek.ZeroSeekMod;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class TPSMonitor {
    private volatile TPSState state = TPSState.NORMAL;
    private final List<Consumer<TPSState>> stateChangeListeners = new CopyOnWriteArrayList<>();

    public void addStateChangeListener(Consumer<TPSState> listener) {
        stateChangeListeners.add(listener);
    }

    public void onTick(MinecraftServer server) {
        if (!ZeroSeekMod.CONFIG.tpsGovernorEnabled) {
            state = TPSState.NORMAL;
            return;
        }
        long avgNanos = server.getAverageTickTimeNanos();
        double tps = avgNanos > 0 ? Math.min(20.0, 1_000_000_000.0 / avgNanos) : 20.0;
        state = classify(tps);
        setState(state);
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

    public void setState(TPSState newState) {
        TPSState oldState = this.state;
        this.state = newState;
        if (oldState == TPSState.CRITICAL && newState != TPSState.CRITICAL) {
            for (Consumer<TPSState> listener : stateChangeListeners) {
                try {
                    listener.accept(newState);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
