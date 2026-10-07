package com.zeroseek.light;

import net.fabricmc.api.DedicatedServerModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZeroSeekLightMod implements DedicatedServerModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ZeroSeek-Light");

    @Override
    public void onInitializeServer() {
        LOGGER.info("ZeroSeek Native Bitboard Light Engine initialized (SWMR lock-free lighting active)");
    }
}
