package com.zeroseek.io;

import com.zeroseek.ZeroSeekMod;
import net.minecraft.world.level.ChunkPos;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.InflaterInputStream;

public class ExternalDeltaManager {
    private static final Path BASE_DELTA_DIR = Path.of("world", "region_delta");

    public static void writeChunk(ChunkPos pos, ByteBuffer buffer) throws IOException {
        Path chunkFile = getChunkPath(pos);
        Files.createDirectories(chunkFile.getParent());
        byte[] data = new byte[buffer.remaining()];
        buffer.duplicate().get(data);
        Files.write(chunkFile, data);
    }

    public static DataInputStream readChunk(ChunkPos pos) throws IOException {
        Path chunkFile = getChunkPath(pos);
        if (!Files.exists(chunkFile)) return null;
        byte[] data = Files.readAllBytes(chunkFile);
        return new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data)));
    }

    public static void clearChunk(ChunkPos pos) throws IOException {
        Files.deleteIfExists(getChunkPath(pos));
    }

    public static boolean hasChunk(ChunkPos pos) {
        return Files.exists(getChunkPath(pos));
    }

    public static List<Path> getAllDeltaFiles() throws IOException {
        if (!Files.exists(BASE_DELTA_DIR)) return List.of();
        try (Stream<Path> walk = Files.walk(BASE_DELTA_DIR)) {
            return walk.filter(p -> p.toString().endsWith(".raw")).collect(Collectors.toList());
        }
    }

    public static void deleteChunkFile(Path path) throws IOException {
        Files.deleteIfExists(path);
    }

    public static Path getChunkPath(ChunkPos pos) {
        return BASE_DELTA_DIR
                .resolve("r." + pos.getRegionX() + "." + pos.getRegionZ())
                .resolve("c." + pos.x + "." + pos.z + ".raw");
    }

    public static Path getRegionFolder(ChunkPos pos) {
        return BASE_DELTA_DIR.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ());
    }

    public static ChunkPos parseChunkPos(Path rawFile) {
        String name = rawFile.getFileName().toString(); // c.x.z.raw
        String[] parts = name.replace(".raw", "").split("\\.");
        int x = Integer.parseInt(parts[1]);
        int z = Integer.parseInt(parts[2]);
        return new ChunkPos(x, z);
    }

    public static Path getBasePathFromChunk(ChunkPos pos, Path baseFolder) {
        return baseFolder.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca");
    }
}
