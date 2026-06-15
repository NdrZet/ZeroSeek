package com.zeroseek.io;

import com.zeroseek.ZeroSeekMod;
import com.zeroseek.mixin.RegionFileInvoker;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class RebaseWorker implements Runnable {

    @Override
    public void run() {
        try {
            List<Path> deltaFiles = ExternalDeltaManager.getAllDeltaFiles();
            if (deltaFiles.isEmpty()) return;

            ZeroSeekMod.LOGGER.info("Starting rebase for {} delta chunks", deltaFiles.size());

            for (Path deltaFile : deltaFiles) {
                rebaseChunk(deltaFile);
            }

            ZeroSeekMod.LOGGER.info("Rebase completed");
        } catch (IOException e) {
            ZeroSeekMod.LOGGER.error("Rebase failed", e);
        }
    }

    private void rebaseChunk(Path deltaFile) {
        try {
            ChunkPos pos = ExternalDeltaManager.parseChunkPos(deltaFile);
            byte[] data = Files.readAllBytes(deltaFile);

            Path regionFolder = deltaFile.getParent();
            Path deltaBaseDir = ExternalDeltaManager.getChunkPath(pos).getParent().getParent();
            String relative = deltaBaseDir.relativize(regionFolder).toString();

            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));

            if (regionFolder.toString().contains("DIM-1")) {
                dimension = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_nether"));
            } else if (regionFolder.toString().contains("DIM1")) {
                dimension = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_end"));
            }

            RegionStorageInfo info = new RegionStorageInfo("minecraft", dimension, "region");
            Path baseFilePath = regionFolder.resolveSibling("region").resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca");

            if (!Files.exists(baseFilePath)) {
                baseFilePath = regionFolder.getParent().resolve("region").resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca");
            }

            if (!Files.exists(baseFilePath.getParent())) {
                Files.createDirectories(baseFilePath.getParent());
            }

            RegionFile baseFile = new RegionFile(info, baseFilePath, baseFilePath.getParent(), false);
            try {
                RebaseState.setRebasing(true);
                ((RegionFileInvoker) baseFile).zeroseek$invokeWrite(pos, ByteBuffer.wrap(data));
            } finally {
                RebaseState.setRebasing(false);
                baseFile.close();
            }

            ExternalDeltaManager.deleteChunkFile(deltaFile);
            if (ZeroSeekMod.CONFIG.debugMmap) ZeroSeekMod.LOGGER.debug("Rebased chunk {}", pos);

        } catch (Throwable e) {
            ZeroSeekMod.LOGGER.error("Rebase failed for {}", deltaFile, e);
        }
    }
}
