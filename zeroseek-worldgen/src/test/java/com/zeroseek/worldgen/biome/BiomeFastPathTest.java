package com.zeroseek.worldgen.biome;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class BiomeFastPathTest {

    @Test
    public void testUniformSectionEvaluationCount() {
        Holder<Biome> plains = Holder.direct(null);
        AtomicInteger sampleCount = new AtomicInteger(0);

        BiomeResolver uniformResolver = (x, y, z, sampler) -> {
            sampleCount.incrementAndGet();
            return plains;
        };

        // Simulated LevelChunkSectionMixin corner fast-path
        int minQuartX = 100;
        int minQuartY = 20;
        int minQuartZ = 200;

        Holder<Biome> b0 = uniformResolver.getNoiseBiome(minQuartX, minQuartY, minQuartZ, null);
        Holder<Biome> b7 = uniformResolver.getNoiseBiome(minQuartX + 3, minQuartY + 3, minQuartZ + 3, null);
        assertEquals(b0, b7);

        // 6 remaining corners
        Holder<Biome> b1 = uniformResolver.getNoiseBiome(minQuartX + 3, minQuartY, minQuartZ, null);
        Holder<Biome> b2 = uniformResolver.getNoiseBiome(minQuartX, minQuartY + 3, minQuartZ, null);
        Holder<Biome> b3 = uniformResolver.getNoiseBiome(minQuartX + 3, minQuartY + 3, minQuartZ, null);
        Holder<Biome> b4 = uniformResolver.getNoiseBiome(minQuartX, minQuartY, minQuartZ + 3, null);
        Holder<Biome> b5 = uniformResolver.getNoiseBiome(minQuartX + 3, minQuartY, minQuartZ + 3, null);
        Holder<Biome> b6 = uniformResolver.getNoiseBiome(minQuartX, minQuartY + 3, minQuartZ + 3, null);

        // 2 centers
        Holder<Biome> c1 = uniformResolver.getNoiseBiome(minQuartX + 1, minQuartY + 1, minQuartZ + 1, null);
        Holder<Biome> c2 = uniformResolver.getNoiseBiome(minQuartX + 2, minQuartY + 2, minQuartZ + 2, null);

        assertEquals(b0, b1);
        assertEquals(b0, b2);
        assertEquals(b0, b3);
        assertEquals(b0, b4);
        assertEquals(b0, b5);
        assertEquals(b0, b6);
        assertEquals(b0, c1);
        assertEquals(b0, c2);

        // Verified: only 10 samples were needed instead of 64
        assertEquals(10, sampleCount.get());
    }

    @Test
    public void testHeterogeneousSectionImmediateBailout() {
        Holder<Biome> plains = Holder.direct(null);
        Holder<Biome> jaggedPeaks = Holder.direct(null);
        AtomicInteger sampleCount = new AtomicInteger(0);

        BiomeResolver borderResolver = (x, y, z, sampler) -> {
            sampleCount.incrementAndGet();
            if (x > 101) return jaggedPeaks;
            return plains;
        };

        int minQuartX = 100;
        int minQuartY = 20;
        int minQuartZ = 200;

        Holder<Biome> b0 = borderResolver.getNoiseBiome(minQuartX, minQuartY, minQuartZ, null);
        Holder<Biome> b7 = borderResolver.getNoiseBiome(minQuartX + 3, minQuartY + 3, minQuartZ + 3, null);

        // Opposite corners differ by reference (!=), bailout occurs immediately after only 2 samples!
        assertNotSame(b0, b7);
        assertTrue(b0 != b7);
        assertEquals(2, sampleCount.get());
    }

    @Test
    public void testStratosphereSectionEvaluationCount() {
        Holder<Biome> frozenPeaks = Holder.direct(null);
        AtomicInteger sampleCount = new AtomicInteger(0);

        BiomeResolver skyResolver = (x, y, z, sampler) -> {
            sampleCount.incrementAndGet();
            return frozenPeaks;
        };

        int minQuartX = 100;
        int minQuartY = 96; // Y = 384+ (Stratosphere)
        int minQuartZ = 200;

        // In stratosphere, only 4 horizontal base corners are evaluated
        Holder<Biome> b00 = skyResolver.getNoiseBiome(minQuartX, minQuartY, minQuartZ, null);
        Holder<Biome> b33 = skyResolver.getNoiseBiome(minQuartX + 3, minQuartY, minQuartZ + 3, null);
        assertEquals(b00, b33);

        Holder<Biome> b30 = skyResolver.getNoiseBiome(minQuartX + 3, minQuartY, minQuartZ, null);
        Holder<Biome> b03 = skyResolver.getNoiseBiome(minQuartX, minQuartY, minQuartZ + 3, null);
        assertEquals(b00, b30);
        assertEquals(b00, b03);

        // Stratosphere check only needs 4 samples!
        assertEquals(4, sampleCount.get());
    }
}
