package com.zeroseek.io;

import com.zeroseek.ZeroSeekMod;
import com.zeroseek.mixin.RegionFileInvoker;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class RebaseWorker implements Runnable {

    @Override
    public void run() {
        try {
            List<Path> deltaFiles = ExternalDeltaManager.getAllDeltaFiles();
            if (deltaFiles.isEmpty()) return;

            ZeroSeekMod.LOGGER.info("Starting rebase for {} delta chunks", deltaFiles.size());

            // Group delta files by target base region file (.mca)
            Map<Path, List<Path>> regionGroups = new LinkedHashMap<>();
            for (Path deltaFile : deltaFiles) {
                try {
                    Path baseFilePath = computeBaseFilePath(deltaFile);
                    regionGroups.computeIfAbsent(baseFilePath, k -> new ArrayList<>()).add(deltaFile);
                } catch (Exception e) {
                    ZeroSeekMod.LOGGER.error("Failed to determine base region file for delta file {}", deltaFile, e);
                }
            }

            for (Map.Entry<Path, List<Path>> entry : regionGroups.entrySet()) {
                Path baseFilePath = entry.getKey();
                List<Path> deltaFilesForRegion = entry.getValue();
                try {
                    rebaseRegion(baseFilePath, deltaFilesForRegion);
                } catch (Throwable e) {
                    ZeroSeekMod.LOGGER.error("Rebase failed for region {}", baseFilePath, e);
                }
            }

            ZeroSeekMod.LOGGER.info("Rebase completed");
        } catch (Throwable e) {
            ZeroSeekMod.LOGGER.error("Rebase failed", e);
        }
    }

    private void rebaseRegion(Path baseFilePath, List<Path> deltaFilesForRegion) throws Exception {
        Path parentDir = baseFilePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        // Invalidate MmapLruCache EXACTLY ONCE per region file before writing chunks
        MmapLruCache.invalidate(baseFilePath);

        ResourceKey<Level> dimension = detectDimension(baseFilePath);
        RegionStorageInfo info = new RegionStorageInfo("minecraft", dimension, "chunk");

        // Open RegionFile EXACTLY ONCE per batch/region
        RegionFile baseFile = new RegionFile(info, baseFilePath, baseFilePath.getParent(), false);
        try {
            RebaseState.setRebasing(true);
            for (Path deltaFile : deltaFilesForRegion) {
                ChunkPos pos = ExternalDeltaManager.parseChunkPos(deltaFile);
                byte[] data = Files.readAllBytes(deltaFile);
                ((RegionFileInvoker) baseFile).zeroseek$invokeWrite(pos, ByteBuffer.wrap(data));
                ExternalDeltaManager.deleteChunkFile(deltaFile);
                if (ZeroSeekMod.CONFIG.debugMmap) ZeroSeekMod.LOGGER.debug("Rebased chunk {}", pos);
            }
        } finally {
            RebaseState.setRebasing(false);
            baseFile.close();
        }

        // Clean up empty region delta folder
        cleanUpEmptyDeltaFolder(deltaFilesForRegion);
    }

    public static Path computeBaseFilePath(Path deltaFile) {
        Path rFolder = deltaFile.getParent();
        if (rFolder == null) {
            throw new IllegalArgumentException("Invalid delta file path (no parent): " + deltaFile);
        }

        Path curr = rFolder;
        Path regionDeltaDir = null;
        while (curr != null) {
            Path fileName = curr.getFileName();
            if (fileName != null && "region_delta".equals(fileName.toString())) {
                regionDeltaDir = curr;
                break;
            }
            curr = curr.getParent();
        }

        Path baseRegionDir;
        if (regionDeltaDir != null) {
            baseRegionDir = regionDeltaDir.resolveSibling("region");
        } else {
            baseRegionDir = rFolder.getParent() != null ? rFolder.getParent().resolveSibling("region") : Path.of("region");
        }

        ChunkPos pos = ExternalDeltaManager.parseChunkPos(deltaFile);
        return baseRegionDir.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca").normalize();
    }

    public static ResourceKey<Level> detectDimension(Path path) {
        Path dimDir = null;
        Path curr = path.getParent();
        while (curr != null) {
            String name = curr.getFileName() != null ? curr.getFileName().toString() : "";
            if ("region".equals(name) || "region_delta".equals(name)) {
                dimDir = curr.getParent();
                break;
            }
            curr = curr.getParent();
        }

        if (dimDir != null) {
            String dimName = dimDir.getFileName() != null ? dimDir.getFileName().toString() : "";
            if ("DIM-1".equals(dimName)) {
                return ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_nether"));
            }
            if ("DIM1".equals(dimName)) {
                return ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_end"));
            }

            Path parent = dimDir.getParent();
            if (parent != null) {
                Path grandParent = parent.getParent();
                if (grandParent != null && "dimensions".equals(grandParent.getFileName().toString())) {
                    String namespace = parent.getFileName().toString();
                    String dimPath = dimName;
                    return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(namespace, dimPath));
                }
                if ("dimensions".equals(parent.getFileName().toString())) {
                    return ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace(dimName));
                }
            }
        }

        // Fallback: check path string
        String normalized = path.toString().replace('\\', '/');
        if (normalized.contains("/DIM-1/") || normalized.contains("/DIM-1")) {
            return ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_nether"));
        }
        if (normalized.contains("/DIM1/") || normalized.contains("/DIM1")) {
            return ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_end"));
        }
        int dimIdx = normalized.indexOf("/dimensions/");
        if (dimIdx != -1) {
            String sub = normalized.substring(dimIdx + "/dimensions/".length());
            int sepIdx = sub.indexOf("/region");
            if (sepIdx != -1) {
                String dimSub = sub.substring(0, sepIdx);
                String[] parts = dimSub.split("/");
                if (parts.length >= 2) {
                    return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(parts[0], parts[1]));
                } else if (parts.length == 1) {
                    return ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace(parts[0]));
                }
            }
        }

        return ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    }

    private static void cleanUpEmptyDeltaFolder(List<Path> deltaFilesForRegion) {
        if (deltaFilesForRegion == null || deltaFilesForRegion.isEmpty()) {
            return;
        }
        try {
            Path rFolder = deltaFilesForRegion.getFirst().getParent();
            if (rFolder != null && Files.exists(rFolder)) {
                try (Stream<Path> stream = Files.list(rFolder)) {
                    if (stream.findAny().isEmpty()) {
                        Files.deleteIfExists(rFolder);
                        Path regionDeltaDir = rFolder.getParent();
                        if (regionDeltaDir != null && Files.exists(regionDeltaDir)) {
                            try (Stream<Path> deltaStream = Files.list(regionDeltaDir)) {
                                if (deltaStream.findAny().isEmpty()) {
                                    Files.deleteIfExists(regionDeltaDir);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}
