package com.zeroseek.async;

import com.zeroseek.ZeroSeekMod;
import com.zeroseek.config.ZeroSeekConfig;
import net.minecraft.server.level.ServerLevel;
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
    private final HardenedWorkerPool loaderPool;
    private final ConcurrentHashMap<ChunkPos, CompletableFuture<ChunkAccess>> loadingCache;
    private final ZeroSeekConfig config;

    public AsyncChunkService(ZeroSeekConfig config) {
        this.config = config;
        this.parserPool = new HardenedWorkerPool(
                "zeroseek-parser",
                config.chunkParserThreads,
                config.chunkParserMaxQueue
        );
        this.loaderPool = new HardenedWorkerPool(
                "zeroseek-loader",
                config.chunkLoaderThreads,
                config.chunkLoaderMaxQueue
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

    /**
     * Prefetch a chunk by delegating to the vanilla scheduleChunkLoad via invoker.
     * The resulting future is tracked for deduplication.
     */
    public void prefetchChunk(ServerLevel level, ChunkPos pos) {
        if (!config.chunkPrefetchEnabled) return;
        if (isCachedOrLoading(pos)) return;

        // Fire-and-forget prefetch: vanilla I/O (async) + our parser pool (redirected via mixin)
        // We cannot easily invoke private scheduleChunkLoad from here without reflection/invoker.
        // Instead, the ChunkPrefetcher uses ChunkMapInvoker directly.
        // This method is kept for future direct prefetch pipeline (MMap + parse in loaderPool).

        ZeroSeekMod.LOGGER.debug("Prefetch requested for chunk {}", pos);
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
