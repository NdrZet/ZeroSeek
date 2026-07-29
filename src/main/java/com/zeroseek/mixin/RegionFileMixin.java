package com.zeroseek.mixin;

import com.zeroseek.ZeroSeekMod;
import com.zeroseek.io.ExternalDeltaManager;
import com.zeroseek.io.MmapLruCache;
import com.zeroseek.io.MmapRegionIo;
import com.zeroseek.io.RebaseState;
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

    @Unique
    private boolean zeroseek$mmapEnabled = true;

    @Inject(method = "getChunkDataInputStream", at = @At("HEAD"), cancellable = true)
    private void zeroseek$read(ChunkPos pos, CallbackInfoReturnable<DataInputStream> cir) throws IOException {
        if (!zeroseek$isChunkStorage()) return;

        // 1. Try delta first — it takes priority over base .mca for modified chunks
        zeroseek$checkDeltaRead(pos, cir);
        if (cir.isCancelled()) return;

        // 2. Fallback to MMap for base layer
        if (ZeroSeekMod.CONFIG.mmapEnabled && this.zeroseek$mmapEnabled) {
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
            } else {
                // Mapping failed; disable MMap for this region file to avoid repeated errors.
                this.zeroseek$mmapEnabled = false;
            }
        }

        if (ZeroSeekMod.CONFIG.debugMmap) ZeroSeekMod.LOGGER.debug("Chunk {} falling back to VANILLA", pos);
    }

    @Unique
    private void zeroseek$checkDeltaRead(ChunkPos pos, CallbackInfoReturnable<DataInputStream> cir) throws IOException {
        if (!ZeroSeekMod.CONFIG.deltaLayerEnabled) return;

        if (ExternalDeltaManager.hasChunk(pos)) {
            DataInputStream stream = ExternalDeltaManager.readChunk(pos);
            if (stream != null) {
                if (ZeroSeekMod.CONFIG.debugMmap) ZeroSeekMod.LOGGER.debug("Chunk {} served from DELTA", pos);
                cir.setReturnValue(stream);
            }
        }
    }

    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    private void zeroseek$write(ChunkPos pos, ByteBuffer buffer, CallbackInfo ci) {
        if (!zeroseek$isChunkStorage()) return;
        if (!ZeroSeekMod.CONFIG.deltaLayerEnabled) return;
        if (RebaseState.isRebasing()) return; // let rebase write directly to .mca

        try {
            // Vanilla RegionFile$ChunkBuffer already prepends [length(4)][type(1)] to the payload.
            // We store the buffer as-is so ExternalDeltaManager can read it back directly.
            ExternalDeltaManager.writeChunk(pos, buffer);
            if (ZeroSeekMod.CONFIG.debugMmap) ZeroSeekMod.LOGGER.debug("Chunk {} written to DELTA", pos);
            ci.cancel();
        } catch (IOException e) {
            ZeroSeekMod.LOGGER.error("Delta write failed for {}", pos, e);
        }
    }

    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void zeroseek$clear(ChunkPos pos, CallbackInfo ci) {
        if (!zeroseek$isChunkStorage()) return;
        if (!ZeroSeekMod.CONFIG.deltaLayerEnabled) return;

        try {
            ExternalDeltaManager.clearChunk(pos);
            ci.cancel();
        } catch (IOException e) {
            ZeroSeekMod.LOGGER.error("Delta clear failed for {}", pos, e);
        }
    }

    @Inject(method = "close", at = @At("RETURN"))
    private void zeroseek$close(CallbackInfo ci) {
        // MMap lifecycle is managed by MmapLruCache; RegionFile.close does not unmap here.
    }
}
