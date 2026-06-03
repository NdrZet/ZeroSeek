package com.zeroseek.io;

import com.zeroseek.ZeroSeekMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class RebaseWorker implements Runnable {

    private static final MethodHandle REGION_FILE_CTOR;
    private static final MethodHandle REGION_FILE_WRITE;

    static {
        try {
            var lookup = MethodHandles.privateLookupIn(RegionFile.class, MethodHandles.lookup());
            REGION_FILE_CTOR = lookup.findConstructor(RegionFile.class,
                    MethodType.methodType(void.class, RegionStorageInfo.class, Path.class, Path.class, boolean.class));
            REGION_FILE_WRITE = lookup.findVirtual(RegionFile.class, "write",
                    MethodType.methodType(void.class, ChunkPos.class, ByteBuffer.class));
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize rebase handles", e);
        }
    }

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

            // Determine base folder from delta path structure
            Path regionFolder = deltaFile.getParent();
            Path deltaBaseDir = ExternalDeltaManager.getChunkPath(pos).getParent().getParent();
            String relative = deltaBaseDir.relativize(regionFolder).toString();
            
            // Default to overworld
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
            
            // Detect dimension from path
            if (regionFolder.toString().contains("DIM-1")) {
                dimension = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_nether"));
            } else if (regionFolder.toString().contains("DIM1")) {
                dimension = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_end"));
            }

            RegionStorageInfo info = new RegionStorageInfo("minecraft", dimension, "region");
            Path baseFilePath = regionFolder.resolveSibling("region").resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca");
            
            // Adjust base path if dimension subfolder exists
            if (!Files.exists(baseFilePath)) {
                baseFilePath = regionFolder.getParent().resolve("region").resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca");
            }

            if (!Files.exists(baseFilePath.getParent())) {
                Files.createDirectories(baseFilePath.getParent());
            }

            RegionFile baseFile = (RegionFile) REGION_FILE_CTOR.invoke(info, baseFilePath, baseFilePath.getParent(), false);
            try {
                REGION_FILE_WRITE.invoke(baseFile, pos, ByteBuffer.wrap(data));
            } finally {
                baseFile.close();
            }

            ExternalDeltaManager.deleteChunkFile(deltaFile);
            ZeroSeekMod.LOGGER.debug("Rebased chunk {}", pos);

        } catch (Throwable e) {
            ZeroSeekMod.LOGGER.error("Rebase failed for {}", deltaFile, e);
        }
    }
}
