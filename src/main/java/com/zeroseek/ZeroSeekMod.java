package com.zeroseek;

import com.zeroseek.config.ZeroSeekConfig;
import net.fabricmc.api.DedicatedServerModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZeroSeekMod implements DedicatedServerModInitializer {
    public static final String MOD_ID = "zeroseek";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ZeroSeekConfig CONFIG;

    @Override
    public void onInitializeServer() {
        CONFIG = ZeroSeekConfig.load();
        LOGGER.info("========================================");
        LOGGER.info("  ZeroSeek v1.0.0");
        LOGGER.info("  MMap Chunk Engine");
        LOGGER.info("========================================");
        LOGGER.info("MMap enabled: {}", CONFIG.mmapEnabled);
        LOGGER.info("Max mapped bytes: {}", CONFIG.maxMappedBytes);
    }
}
