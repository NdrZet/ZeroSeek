package com.zeroseek.worldgen.palette;

import net.minecraft.world.level.chunk.Palette;

public interface CompactingPackedIntegerArray {
    <T> void zeroseek$compact(Palette<T> srcPalette, Palette<T> dstPalette, short[] out);
}
