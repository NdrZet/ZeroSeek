package com.zeroseek.mixin;

import com.zeroseek.ZeroSeekMod;
import com.zeroseek.io.MmapLruCache;
import com.zeroseek.io.MmapRegionIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

@Mixin(RegionFile.class)
public class RegionFileMixin {

    @Shadow
    private java.nio.file.Path path;

    @Shadow
    private RegionStorageInfo info;

    @Unique
    private boolean zeroseek$isChunkStorage() {
        return this.info != null && "chunk".equals(this.info.type());
    }

    @Inject(method = "getChunkDataInputStream", at = @At("HEAD"), cancellable = true)
    private void zeroseek$read(ChunkPos pos, CallbackInfoReturnable<DataInputStream> cir) throws IOException {
        if (!zeroseek$isChunkStorage()) return;

        // MMap for base layer fast zero-copy reads
        if (ZeroSeekMod.CONFIG.mmapEnabled) {
            MmapRegionIo io = MmapLruCache.acquire(path);
            if (io != null) {
                try {
                    DataInputStream stream = io.read(pos);
                    if (stream != null) {
                        if (ZeroSeekMod.CONFIG.debugMmap) ZeroSeekMod.LOGGER.debug("Chunk {} served from MMAP", pos);
                        cir.setReturnValue(stream);
                        return;
                    }
                } finally {
                    MmapLruCache.release(path);
                }
                // Chunk is not present in base .mca (offset is 0).
                cir.setReturnValue(null);
                return;
            } else if (!java.nio.file.Files.exists(path) || java.nio.file.Files.size(path) == 0) {
                // Base region file does not exist or is empty; chunk does not exist yet.
                cir.setReturnValue(null);
                return;
            }
        }

        if (ZeroSeekMod.CONFIG.debugMmap) ZeroSeekMod.LOGGER.debug("Chunk {} falling back to VANILLA", pos);
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void zeroseek$onWrite(ChunkPos pos, ByteBuffer buffer, CallbackInfo ci) {
        if (!zeroseek$isChunkStorage()) return;
        if (ZeroSeekMod.CONFIG.mmapEnabled && this.path != null) {
            MmapLruCache.invalidate(this.path);
        }
    }

    @Inject(method = "clear", at = @At("RETURN"))
    private void zeroseek$onClear(ChunkPos pos, CallbackInfo ci) {
        if (!zeroseek$isChunkStorage()) return;
        if (ZeroSeekMod.CONFIG.mmapEnabled && this.path != null) {
            MmapLruCache.invalidate(this.path);
        }
    }

    @Inject(method = "close", at = @At("RETURN"))
    private void zeroseek$close(CallbackInfo ci) {
        if (ZeroSeekMod.CONFIG.mmapEnabled && this.path != null) {
            MmapLruCache.invalidate(this.path);
        }
    }
}
