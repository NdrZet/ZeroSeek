package com.zeroseek.worldgen.biome;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SearchTreeCacheTest {

    @Test
    public void testSearchTreeCacheHitAndMiss() {
        Object mockSource = new Object();
        Climate.TargetPoint target1 = Climate.target(0.5f, -0.2f, 0.1f, 0.0f, 0.0f, 0.3f);
        Climate.TargetPoint target2 = Climate.target(-0.5f, 0.2f, -0.1f, 0.0f, 0.0f, -0.3f);

        Holder<Biome> mockBiome1 = Holder.direct(null);
        Holder<Biome> mockBiome2 = Holder.direct(null);

        assertNull(SearchTreeCache.get(mockSource, target1));

        SearchTreeCache.put(mockSource, target1, mockBiome1);
        assertSame(mockBiome1, SearchTreeCache.get(mockSource, target1));
        assertNull(SearchTreeCache.get(mockSource, target2));

        SearchTreeCache.put(mockSource, target2, mockBiome2);
        assertSame(mockBiome2, SearchTreeCache.get(mockSource, target2));
        assertSame(mockBiome1, SearchTreeCache.get(mockSource, target1));
    }

    @Test
    public void testClimateColumnCacheKeys() {
        int blockX = 128;
        int blockZ = -256;
        long key1 = ClimateColumnCache.makeColumnKey(blockX, blockZ);
        long key2 = ClimateColumnCache.makeColumnKey(blockX + 4, blockZ);

        assertNotEquals(key1, key2);

        int idx1 = ClimateColumnCache.getColumnIndex(key1);
        int idx2 = ClimateColumnCache.getColumnIndex(key2);

        assertTrue(idx1 >= 0 && idx1 < ClimateColumnCache.CACHE_SIZE);
        assertTrue(idx2 >= 0 && idx2 < ClimateColumnCache.CACHE_SIZE);
    }
}
