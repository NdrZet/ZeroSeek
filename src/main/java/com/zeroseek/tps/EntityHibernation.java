package com.zeroseek.tps;

import com.zeroseek.ZeroSeekMod;
import com.zeroseek.mixin.LevelChunkMixin;
import net.minecraft.world.level.chunk.LevelChunk;

public class EntityHibernation {
    public static boolean shouldHibernate(LevelChunk chunk) {
        if (!ZeroSeekMod.CONFIG.tpsGovernorEnabled || !ZeroSeekMod.CONFIG.entityHibernationEnabled) {
            return false;
        }
        long loadedAt = ((LevelChunkMixin) (Object) chunk).zeroseek$getLoadedAt();
        long ageMs = System.currentTimeMillis() - loadedAt;
        if (ageMs < ZeroSeekMod.CONFIG.hibernateMinAgeMs) {
            return false;
        }
        TPSState state = ZeroSeekMod.TPS_MONITOR.getState();
        return state == TPSState.CRITICAL
                || (state == TPSState.STRESS && ageMs > ZeroSeekMod.CONFIG.hibernateStressAgeMs);
    }
}
