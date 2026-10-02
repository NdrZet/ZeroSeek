package com.zeroseek.worldgen.random;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;

/**
 * Thread-safe simplified random source adapted from C2ME.
 * Replaces vanilla CAS/AtomicLong loops in {@link LegacyRandomSource}
 * with an unsynchronized local primitive long seed for maximum performance.
 */
public class SimplifiedAtomicSimpleRandom extends LegacyRandomSource {

    private static final int INT_BITS = 48;
    private static final long SEED_MASK = 281474976710655L;
    private static final long MULTIPLIER = 25214903917L;
    private static final long INCREMENT = 11L;

    private long seed;

    public SimplifiedAtomicSimpleRandom(long seed) {
        super(0L);
        this.setSeed(seed);
    }

    @Override
    public RandomSource fork() {
        return new SingleThreadedRandomSource(this.nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new LegacyRandomSource.LegacyPositionalRandomFactory(this.nextLong());
    }

    @Override
    public void setSeed(long seed) {
        this.seed = (seed ^ MULTIPLIER) & SEED_MASK;
    }

    @Override
    public int next(int bits) {
        long nextSeed = (this.seed * MULTIPLIER + INCREMENT) & SEED_MASK;
        this.seed = nextSeed;
        return (int) (nextSeed >> (INT_BITS - bits));
    }
}
