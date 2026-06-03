package com.zeroseek.io;

import com.zeroseek.ZeroSeekMod;
import net.minecraft.world.level.ChunkPos;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
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
        if (data.length < 5) {
            ZeroSeekMod.LOGGER.warn("Delta chunk {} file too small ({} bytes)", pos, data.length);
            return null;
        }

        // ChunkBuffer writes [4 bytes length][1 byte compressionType][compressedData]
        // Skip the 4-byte length prefix
        byte compressionType = data[4];
        int payloadLen = data.length - 5;
        if (payloadLen <= 0) {
            ZeroSeekMod.LOGGER.warn("Delta chunk {} has empty payload (file={} bytes)", pos, data.length);
            return null;
        }

        // Debug hex header
        if (ZeroSeekMod.CONFIG.debugMmap) {
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(data.length, 20); i++) hex.append(String.format("%02X ", data[i]));
            ZeroSeekMod.LOGGER.debug("Delta chunk {} header: [{}] type={} payloadLen={}", pos, hex.toString().trim(), compressionType, payloadLen);
        }

        byte[] compressed = new byte[payloadLen];
        System.arraycopy(data, 5, compressed, 0, payloadLen);

        InputStream input = new ByteArrayInputStream(compressed);
        switch (compressionType) {
            case 1 -> input = new GZIPInputStream(input);
            case 2 -> input = new InflaterInputStream(input);
            case 3 -> { /* uncompressed */ }
            default -> {
                ZeroSeekMod.LOGGER.error("Unsupported compression type {} in delta chunk {} (hex header: {})", compressionType, pos,
                    String.format("%02X %02X %02X %02X %02X", data[0], data[1], data[2], data[3], data[4]));
                return null;
            }
        }
        return new DataInputStream(input);
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
