package com.zeroseek.mixin;

import com.zeroseek.ZeroSeekMod;
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
    private void zeroseek$read(ChunkPos pos, CallbackInfoReturnable<DataInputStream> cir) {
        if (!ZeroSeekMod.CONFIG.mmapEnabled) return;

        if (this.zeroseek$mmapEnabled && this.zeroseek$mmapIo == null) {
            try {
                if (Files.exists(path) && Files.size(path) > 0) {
                    this.zeroseek$mmapIo = new MmapRegionIo(path);
                    ZeroSeekMod.LOGGER.debug("Mmap initialized for {}", path);
                }
            } catch (Exception e) {
                ZeroSeekMod.LOGGER.error("Mmap init failed for {}", path, e);
                this.zeroseek$mmapEnabled = false;
            }
        }

        if (this.zeroseek$mmapIo != null) {
            DataInputStream stream = this.zeroseek$mmapIo.read(pos);
            if (stream != null) {
                cir.setReturnValue(stream);
            }
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
