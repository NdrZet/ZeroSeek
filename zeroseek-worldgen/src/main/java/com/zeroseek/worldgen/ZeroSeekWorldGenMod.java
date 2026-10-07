package com.zeroseek.worldgen;

import com.zeroseek.ZeroSeekMod;
import com.zeroseek.worldgen.lock.SpatialLockManager;
import net.fabricmc.api.DedicatedServerModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public class ZeroSeekWorldGenMod implements DedicatedServerModInitializer {
    public static final String MOD_ID = "zeroseek-worldgen";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static SpatialLockManager SPATIAL_LOCK_MANAGER;

    @Override
    public void onInitializeServer() {
        if (ZeroSeekMod.CONFIG != null && ZeroSeekMod.CONFIG.concurrentWorldGenEnabled) {
            Executor lockExecutor = (ZeroSeekMod.ASYNC_SERVICE != null)
                    ? ZeroSeekMod.ASYNC_SERVICE.getGeneratorPool().getExecutor()
                    : ForkJoinPool.commonPool();
            SPATIAL_LOCK_MANAGER = new SpatialLockManager(lockExecutor);

            if (ZeroSeekMod.TPS_MONITOR != null) {
                ZeroSeekMod.TPS_MONITOR.addStateChangeListener(state -> {
                    if (SPATIAL_LOCK_MANAGER != null) {
                        SPATIAL_LOCK_MANAGER.resetGovernorPermits();
                    }
                });
            }

            LOGGER.info("Concurrent WorldGen & SpatialLockManager initialized (generator threads={})", ZeroSeekMod.CONFIG.chunkGeneratorThreads);
        } else {
            LOGGER.info("Concurrent WorldGen is disabled in configuration");
        }

        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(net.minecraft.commands.Commands.literal("zeroseek")
                .then(net.minecraft.commands.Commands.literal("worldgen_tps").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                        com.zeroseek.worldgen.telemetry.WorldGenTelemetry.formatSummary()), false);
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("worldgen").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                        com.zeroseek.worldgen.telemetry.WorldGenTelemetry.formatSummary()), false);
                    return 1;
                }))
            );
        });
    }
}
