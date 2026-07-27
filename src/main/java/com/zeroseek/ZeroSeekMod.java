package com.zeroseek;

import com.zeroseek.async.AsyncChunkService;
import com.zeroseek.async.affinity.PlatformAffinity;
import com.zeroseek.chunk.ChunkPrefetcher;
import com.zeroseek.config.ZeroSeekConfig;
import com.zeroseek.io.MadviseHelper;
import com.zeroseek.io.RebaseWorker;
import com.zeroseek.tps.AdaptiveSimulation;
import com.zeroseek.tps.TPSMonitor;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
    public static TPSMonitor TPS_MONITOR;
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
        LOGGER.info("TPS governor enabled: {}", CONFIG.tpsGovernorEnabled);

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

        if (CONFIG.cpuAffinityEnabled) {
            PlatformAffinity.logStatus();
        } else {
            LOGGER.info("CPU affinity disabled in config");
        }

        if (MadviseHelper.supported()) {
            LOGGER.info("MMap prefetch enabled");
        } else {
            LOGGER.info("MMap prefetch not available on this platform");
        }

        if (CONFIG.tpsGovernorEnabled) {
            TPS_MONITOR = new TPSMonitor();
            ServerLifecycleEvents.SERVER_STARTED.register(AdaptiveSimulation::initialize);
            LOGGER.info("TPS governor initialized");
        }

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> shutdown());
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
