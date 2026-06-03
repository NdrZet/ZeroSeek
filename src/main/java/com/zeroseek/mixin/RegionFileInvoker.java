package com.zeroseek.mixin;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.IOException;
import java.nio.ByteBuffer;

@Mixin(RegionFile.class)
public interface RegionFileInvoker {

    @Invoker("write")
    void zeroseek$invokeWrite(ChunkPos pos, ByteBuffer buffer) throws IOException;
}
