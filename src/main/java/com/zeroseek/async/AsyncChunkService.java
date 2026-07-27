package com.zeroseek.async;

import com.zeroseek.ZeroSeekMod;
import com.zeroseek.config.ZeroSeekConfig;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central async service for ZeroSeek Phase 3.
 * Manages dedicated parser/loader pools and deduplication cache for chunk loads.
 */
public class AsyncChunkService {
    private final HardenedWorkerPool parserPool;
    // FUTURE: loaderPool is initialized but not used yet. Reserved for async MMap prefetch / generation pipeline.
    private final HardenedWorkerPool loaderPool;
    private final ConcurrentHashMap<ChunkPos, CompletableFuture<ChunkAccess>> loadingCache;
    private final ZeroSeekConfig config;

    public AsyncChunkService(ZeroSeekConfig config) {
        this.config = config;
        this.parserPool = new HardenedWorkerPool(
                "zeroseek-parser",
                config.chunkParserThreads,
                config.chunkParserMaxQueue,
                config.cpuAffinityEnabled ? config.cpuAffinityCores : null
        );
        this.loaderPool = new HardenedWorkerPool(
                "zeroseek-loader",
                config.chunkLoaderThreads,
                config.chunkLoaderMaxQueue,
                config.cpuAffinityEnabled ? config.cpuAffinityCores : null
        );
        this.loadingCache = new ConcurrentHashMap<>();

        ZeroSeekMod.LOGGER.info(
                "AsyncChunkService initialized: parser={} threads/queue={}, loader={} threads/queue={}",
                config.chunkParserThreads, config.chunkParserMaxQueue,
                config.chunkLoaderThreads, config.chunkLoaderMaxQueue
        );
    }

    /**
     * Returns an active loading future for the given chunk if one exists.
     */
    public CompletableFuture<ChunkAccess> getLoadingFuture(ChunkPos pos) {
        return loadingCache.get(pos);
    }

    /**
     * Checks if a chunk is currently being loaded or already cached.
     */
    public boolean isCachedOrLoading(ChunkPos pos) {
        CompletableFuture<ChunkAccess> future = loadingCache.get(pos);
        if (future == null) return false;
        return !future.isCompletedExceptionally();
    }

    /**
     * Registers a chunk loading future in the deduplication cache.
     * Automatically removes the entry when the future completes.
     */
    public void trackLoadingFuture(ChunkPos pos, CompletableFuture<ChunkAccess> future) {
        if (future == null) return;
        loadingCache.put(pos, future);
        future.whenComplete((result, ex) -> loadingCache.remove(pos));
    }

    public HardenedWorkerPool getParserPool() {
        return parserPool;
    }

    public HardenedWorkerPool getLoaderPool() {
        return loaderPool;
    }

    public int getCacheSize() {
        return loadingCache.size();
    }

    public void shutdown() {
        parserPool.shutdown();
        loaderPool.shutdown();
        ZeroSeekMod.LOGGER.info("AsyncChunkService shut down");
    }
}
