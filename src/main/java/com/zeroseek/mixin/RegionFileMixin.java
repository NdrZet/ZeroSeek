package com.zeroseek.mixin;

import com.zeroseek.ZeroSeekMod;
import com.zeroseek.io.ExternalDeltaManager;
import com.zeroseek.io.MmapRegionIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
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
import java.nio.file.Files;
import java.nio.file.Path;

@Mixin(RegionFile.class)
public class RegionFileMixin {

    @Shadow
    private Path path;

    @Unique
    private MmapRegionIo zeroseek$mmapIo;

    @Unique
    private boolean zeroseek$mmapEnabled = true;

    @Inject(method = "getChunkDataInputStream", at = @At("HEAD"), cancellable = true)
    private void zeroseek$read(ChunkPos pos, CallbackInfoReturnable<DataInputStream> cir) throws IOException {
        if (!ZeroSeekMod.CONFIG.mmapEnabled) {
            zeroseek$checkDeltaRead(pos, cir);
            return;
        }

        if (this.zeroseek$mmapEnabled && this.zeroseek$mmapIo == null) {
            try {
                if (Files.exists(path) && Files.size(path) > 0) {
                    this.zeroseek$mmapIo = new MmapRegionIo(path);
                    if (ZeroSeekMod.CONFIG.debugMmap) ZeroSeekMod.LOGGER.debug("Mmap initialized for {}", path);
                }
            } catch (Exception e) {
                ZeroSeekMod.LOGGER.error("Mmap init failed for {}", path, e);
                this.zeroseek$mmapEnabled = false;
            }
        }

        if (this.zeroseek$mmapIo != null) {
            DataInputStream stream = this.zeroseek$mmapIo.read(pos);
            if (stream != null) {
                if (ZeroSeekMod.CONFIG.debugMmap) ZeroSeekMod.LOGGER.debug("Chunk {} served from MMAP", pos);
                cir.setReturnValue(stream);
                return;
            }
        }

        zeroseek$checkDeltaRead(pos, cir);
    }

    @Unique
    private void zeroseek$checkDeltaRead(ChunkPos pos, CallbackInfoReturnable<DataInputStream> cir) throws IOException {
        if (!ZeroSeekMod.CONFIG.deltaLayerEnabled) return;

        if (ExternalDeltaManager.hasChunk(pos)) {
            DataInputStream stream = ExternalDeltaManager.readChunk(pos);
            if (stream != null) {
                if (ZeroSeekMod.CONFIG.debugMmap) ZeroSeekMod.LOGGER.debug("Chunk {} served from DELTA", pos);
                cir.setReturnValue(stream);
                return;
            }
        }
        if (ZeroSeekMod.CONFIG.debugMmap) ZeroSeekMod.LOGGER.debug("Chunk {} falling back to VANILLA", pos);
    }

    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    private void zeroseek$write(ChunkPos pos, ByteBuffer buffer, CallbackInfo ci) {
        if (!ZeroSeekMod.CONFIG.deltaLayerEnabled) return;

        try {
            ExternalDeltaManager.writeChunk(pos, buffer);
            if (ZeroSeekMod.CONFIG.debugMmap) ZeroSeekMod.LOGGER.debug("Chunk {} written to DELTA", pos);
            ci.cancel();
        } catch (IOException e) {
            ZeroSeekMod.LOGGER.error("Delta write failed for {}", pos, e);
        }
    }

    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void zeroseek$clear(ChunkPos pos, CallbackInfo ci) {
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
        if (this.zeroseek$mmapIo != null) {
            this.zeroseek$mmapIo.close();
            this.zeroseek$mmapIo = null;
        }
    }
}
