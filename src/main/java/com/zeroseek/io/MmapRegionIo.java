package com.zeroseek.io;

import com.zeroseek.ZeroSeekMod;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class MmapRegionIo {
    private final Arena arena;
    private final MemorySegment segment;
    private final long fileSize;

    public MmapRegionIo(Path path) throws IOException {
        this.fileSize = Files.size(path);
        Arena arena = Arena.ofShared();
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            this.segment = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize, arena);
            if (MadviseHelper.supported()) {
                MadviseHelper.willNeed(this.segment);
                if (ZeroSeekMod.CONFIG.debugMmap) ZeroSeekMod.LOGGER.debug("MMap prefetch WILLNEED for {}", path);
            }
            this.arena = arena;
        } catch (Throwable t) {
            arena.close();
            throw t;
        }
    }

    public DataInputStream read(ChunkPos pos) {
        long start = System.nanoTime();
        try {
            int localX = pos.x & 31;
            int localZ = pos.z & 31;
            int index = localX + localZ * 32;

            int locOffset = index * 4;
            if (locOffset + 4 > 4096) return null;

            int offset = ((segment.get(ValueLayout.JAVA_BYTE, locOffset) & 0xFF) << 16)
                       | ((segment.get(ValueLayout.JAVA_BYTE, locOffset + 1) & 0xFF) << 8)
                       | (segment.get(ValueLayout.JAVA_BYTE, locOffset + 2) & 0xFF);
            int sectorCount = segment.get(ValueLayout.JAVA_BYTE, locOffset + 3) & 0xFF;

            if (offset == 0 || sectorCount == 0) return null;

            long dataOffset = (long) offset * 4096L;
            if (dataOffset + 5 > fileSize) return null;

            int length = ((segment.get(ValueLayout.JAVA_BYTE, dataOffset) & 0xFF) << 24)
                       | ((segment.get(ValueLayout.JAVA_BYTE, dataOffset + 1) & 0xFF) << 16)
                       | ((segment.get(ValueLayout.JAVA_BYTE, dataOffset + 2) & 0xFF) << 8)
                       | (segment.get(ValueLayout.JAVA_BYTE, dataOffset + 3) & 0xFF);

            if (length <= 1 || dataOffset + 4 + length > fileSize) {
                if (ZeroSeekMod.CONFIG.debugMmap) ZeroSeekMod.LOGGER.debug("Mmap chunk {} invalid length {} or out of bounds", pos, length);
                return null;
            }

            byte compressionType = segment.get(ValueLayout.JAVA_BYTE, dataOffset + 4);

            byte[] compressed = new byte[length - 1];
            MemorySegment.copy(segment, dataOffset + 5, MemorySegment.ofArray(compressed), 0, length - 1);

            RegionFileVersion version = RegionFileVersion.fromId(compressionType);
            if (version == null) {
                ZeroSeekMod.LOGGER.error("Unsupported compression type {} in chunk {}", compressionType, pos);
                return null;
            }

            InputStream input = version.wrap(new ByteArrayInputStream(compressed));

            long elapsedUs = (System.nanoTime() - start) / 1000;
            if (ZeroSeekMod.CONFIG.debugMmap) ZeroSeekMod.LOGGER.debug("Mmap read chunk {} took {} us", pos, elapsedUs);
            return new DataInputStream(input);
        } catch (IOException e) {
            ZeroSeekMod.LOGGER.error("Mmap read failed for chunk {}", pos, e);
            return null;
        }
    }

    public void close() {
        if (MadviseHelper.supported() && segment != null) {
            MadviseHelper.dontNeed(this.segment);
            if (ZeroSeekMod.CONFIG.debugMmap) ZeroSeekMod.LOGGER.debug("MMap prefetch release for region");
        }
        arena.close();
    }
}
