package com.zeroseek.io;

import com.zeroseek.ZeroSeekMod;
import com.zeroseek.config.ZeroSeekConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class MmapLruCacheTest {

    @BeforeEach
    public void setup() {
        ZeroSeekMod.CONFIG = new ZeroSeekConfig();
        ZeroSeekMod.CONFIG.mmapEnabled = true;
    }

    @AfterEach
    public void tearDown() {
        MmapLruCache.closeAll();
    }

    @Test
    public void testAcquireNonExistentFileReturnsNull(@TempDir Path tempDir) {
        Path dummy = tempDir.resolve("non_existent.mca");
        assertNull(MmapLruCache.acquire(dummy));
    }

    @Test
    public void testAcquireEmptyFileReturnsNull(@TempDir Path tempDir) throws IOException {
        Path empty = tempDir.resolve("empty.mca");
        Files.createFile(empty);
        assertNull(MmapLruCache.acquire(empty));
    }

    @Test
    public void testAcquireReleaseAndInvalidate(@TempDir Path tempDir) throws IOException {
        Path dummyMca = tempDir.resolve("r.0.0.mca");
        byte[] content = new byte[8192];
        Files.write(dummyMca, content);

        MmapRegionIo io = MmapLruCache.acquire(dummyMca);
        assertNotNull(io);
        assertEquals(8192, io.getFileSize());
        assertEquals(1, MmapLruCache.getMappedRegionCount());

        MmapLruCache.release(dummyMca);

        // Invalidate removes it from cache and closes
        MmapLruCache.invalidate(dummyMca);
        assertEquals(0, MmapLruCache.getMappedRegionCount());
    }
}
