package com.zeroseek.tps;

import com.zeroseek.ZeroSeekMod;
import net.minecraft.world.level.chunk.LevelChunk;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class EntityHibernation {
    private static final Map<LevelChunk, Long> LOADED_AT = Collections.synchronizedMap(new WeakHashMap<>());

    public static boolean shouldHibernate(LevelChunk chunk) {
        if (!ZeroSeekMod.CONFIG.tpsGovernorEnabled || !ZeroSeekMod.CONFIG.entityHibernationEnabled) {
            return false;
        }
        long loadedAt = LOADED_AT.computeIfAbsent(chunk, k -> System.currentTimeMillis());
        long ageMs = System.currentTimeMillis() - loadedAt;
        if (ageMs < ZeroSeekMod.CONFIG.hibernateMinAgeMs) {
            return false;
        }
        TPSState state = ZeroSeekMod.TPS_MONITOR.getState();
        return state == TPSState.CRITICAL
                || (state == TPSState.STRESS && ageMs > ZeroSeekMod.CONFIG.hibernateStressAgeMs);
    }

    public static void unload(LevelChunk chunk) {
        LOADED_AT.remove(chunk);
    }
}
