package com.zeroseek;

import com.zeroseek.async.AsyncChunkService;
import com.zeroseek.chunk.ChunkPrefetcher;
import com.zeroseek.config.ZeroSeekConfig;
import com.zeroseek.io.RebaseWorker;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ZeroSeekMod implements DedicatedServerModInitializer {
    public static final String MOD_ID = "zeroseek";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ZeroSeekConfig CONFIG;
    public static AsyncChunkService ASYNC_SERVICE;
    private static ScheduledExecutorService rebaseScheduler;

    @Override
    public void onInitializeServer() {
        CONFIG = ZeroSeekConfig.load();
        LOGGER.info("========================================");
        LOGGER.info("  ZeroSeek v1.0.0");
        LOGGER.info("  MMap Chunk Engine");
        LOGGER.info("========================================");
        LOGGER.info("MMap enabled: {}", CONFIG.mmapEnabled);
        LOGGER.info("Delta layer enabled: {}", CONFIG.deltaLayerEnabled);
        LOGGER.info("Max mapped bytes: {}", CONFIG.maxMappedBytes);
        LOGGER.info("Rebase interval: {}s", CONFIG.rebaseIntervalSeconds);

        if (CONFIG.deltaLayerEnabled) {
            rebaseScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "zeroseek-rebase");
                t.setDaemon(true);
                return t;
            });
            rebaseScheduler.scheduleAtFixedRate(
                new RebaseWorker(),
                CONFIG.rebaseIntervalSeconds,
                CONFIG.rebaseIntervalSeconds,
                TimeUnit.SECONDS
            );
            LOGGER.info("Rebase scheduler started");
        }

        if (CONFIG.asyncWorkersEnabled) {
            ASYNC_SERVICE = new AsyncChunkService(CONFIG);

            ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
                if (ASYNC_SERVICE == null) return;
                for (var level : server.getAllLevels()) {
                    ChunkPrefetcher.onTick(level);
                }
            });

            LOGGER.info("Async workers started (parser threads={}, loader threads={})",
                    CONFIG.chunkParserThreads, CONFIG.chunkLoaderThreads);
        }
    }

    public static void shutdown() {
        if (rebaseScheduler != null) {
            rebaseScheduler.shutdown();
        }
        if (ASYNC_SERVICE != null) {
            ASYNC_SERVICE.shutdown();
        }
    }
}
