package com.zeroseek.io;

import com.zeroseek.ZeroSeekMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Global LRU cache for memory-mapped region files.
 * <p>
 * Keeps total mapped bytes under {@link ZeroSeekConfig#maxMappedBytes} by evicting
 * the least-recently-used mapping that has no active readers.
 */
public final class MmapLruCache {
    private static final ConcurrentHashMap<Path, Entry> CACHE = new ConcurrentHashMap<>();
    private static final AtomicLong TOTAL_BYTES = new AtomicLong(0);

    /**
     * Evict down to this ratio of {@code maxMappedBytes} to avoid bouncing on the limit.
     */
    private static final double EVICT_TARGET_RATIO = 0.9;

    private static final class Entry {
        final Path path;
        final MmapRegionIo io;
        final long size;
        final AtomicInteger refs = new AtomicInteger(0);
        final AtomicLong lastUsed = new AtomicLong(System.nanoTime());
        volatile boolean closed = false;

        Entry(Path path, MmapRegionIo io, long size) {
            this.path = path;
            this.io = io;
            this.size = size;
        }
    }

    private MmapLruCache() {
    }

    /**
     * Acquires a mapped region for the given path.
     * <p>
     * The caller <b>must</b> call {@link #release(Path)} exactly once for every successful
     * acquire, ideally in a {@code try/finally} block.
     *
     * @return the mapped I/O handle, or {@code null} if the file cannot be mapped
     */
    public static MmapRegionIo acquire(Path path) {
        if (!ZeroSeekMod.CONFIG.mmapEnabled) {
            return null;
        }

        Entry entry = CACHE.get(path);
        boolean created = false;

        if (entry == null) {
            synchronized (MmapLruCache.class) {
                entry = CACHE.get(path);
                if (entry == null) {
                    try {
                        if (!Files.exists(path) || Files.size(path) == 0) {
                            return null;
                        }
                        MmapRegionIo io = new MmapRegionIo(path);
                        long size = io.getFileSize();
                        entry = new Entry(path, io, size);
                        entry.refs.set(1);
                        entry.lastUsed.set(System.nanoTime());
                        TOTAL_BYTES.addAndGet(size);
                        CACHE.put(path, entry);
                        created = true;
                        if (ZeroSeekMod.CONFIG.debugMmap) {
                            ZeroSeekMod.LOGGER.debug("Mmap cached {} ({} bytes, total {})",
                                    path, size, TOTAL_BYTES.get());
                        }
                    } catch (IOException e) {
                        ZeroSeekMod.LOGGER.error("Mmap open failed for {}", path, e);
                        return null;
                    }
                }
            }
        }

        synchronized (entry) {
            if (entry.closed) {
                // Evicted while we were waiting; retry.
                return acquire(path);
            }
            if (!created) {
                entry.refs.incrementAndGet();
            }
            entry.lastUsed.set(System.nanoTime());
        }

        evictIfNeeded();
        return entry.io;
    }

    /**
     * Releases a previously acquired mapping.
     */
    public static void release(Path path) {
        Entry entry = CACHE.get(path);
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            if (entry.closed) {
                return;
            }
            int refs = entry.refs.decrementAndGet();
            if (refs < 0) {
                entry.refs.set(0);
            }
            if (refs == 0) {
                entry.notifyAll();
            }
        }
        evictIfNeeded();
    }

    /**
     * Invalidates the mapping for the given path, blocking briefly until no readers hold it.
     * <p>
     * Used by {@link RebaseWorker} before writing to a base {@code .mca} file to ensure no
     * stale MMap view is read while/after the file is modified.
     */
    public static void invalidate(Path path) {
        Entry entry = CACHE.get(path);
        if (entry == null) {
            return;
        }

        synchronized (entry) {
            if (entry.closed) {
                return;
            }

            // Wait briefly for active readers to finish. Rebase is rare, so a short spin-wait is fine.
            int spins = 0;
            while (entry.refs.get() > 0 && spins < 200) {
                try {
                    entry.wait(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                spins++;
            }

            if (entry.refs.get() > 0) {
                ZeroSeekMod.LOGGER.warn("Could not invalidate mmap for {}: still has {} active readers",
                        path, entry.refs.get());
                return;
            }

            entry.closed = true;
            CACHE.remove(path, entry);
            TOTAL_BYTES.addAndGet(-entry.size);
        }

        entry.io.close();
        if (ZeroSeekMod.CONFIG.debugMmap) {
            ZeroSeekMod.LOGGER.debug("Mmap invalidated {} ({} bytes, total {})",
                    path, entry.size, TOTAL_BYTES.get());
        }
    }

    /**
     * Closes all cached mappings. Called on server shutdown.
     */
    public static void closeAll() {
        for (Entry entry : CACHE.values()) {
            synchronized (entry) {
                if (!entry.closed) {
                    entry.closed = true;
                    TOTAL_BYTES.addAndGet(-entry.size);
                    entry.io.close();
                }
            }
        }
        CACHE.clear();
    }

    public static long getTotalMappedBytes() {
        return TOTAL_BYTES.get();
    }

    public static int getMappedRegionCount() {
        return CACHE.size();
    }

    private static void evictIfNeeded() {
        long max = ZeroSeekMod.CONFIG.maxMappedBytes;
        if (max <= 0) {
            return;
        }
        long target = (long) (max * EVICT_TARGET_RATIO);
        while (TOTAL_BYTES.get() > target) {
            if (!evictOne()) {
                break;
            }
        }
    }

    private static boolean evictOne() {
        long max = ZeroSeekMod.CONFIG.maxMappedBytes;
        if (max <= 0) {
            return false;
        }

        Entry candidate = null;
        long oldest = Long.MAX_VALUE;

        for (Entry e : CACHE.values()) {
            if (e.closed) {
                continue;
            }
            if (e.refs.get() == 0 && e.lastUsed.get() < oldest) {
                candidate = e;
                oldest = e.lastUsed.get();
            }
        }

        if (candidate == null) {
            return false;
        }

        synchronized (candidate) {
            if (candidate.closed || candidate.refs.get() != 0) {
                return false;
            }
            candidate.closed = true;
            CACHE.remove(candidate.path, candidate);
            TOTAL_BYTES.addAndGet(-candidate.size);
        }

        candidate.io.close();
        if (ZeroSeekMod.CONFIG.debugMmap) {
            ZeroSeekMod.LOGGER.debug("Mmap evicted {} ({} bytes, total {})",
                    candidate.path, candidate.size, TOTAL_BYTES.get());
        }
        return true;
    }
}
