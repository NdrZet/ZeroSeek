package com.zeroseek.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zeroseek.ZeroSeekMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ZeroSeekConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = Path.of("config", "zeroseek.json");

    public boolean mmapEnabled = true;
    public boolean deltaLayerEnabled = true;
    public long maxMappedBytes = 2147483648L;
    public int rebaseIntervalSeconds = 300;
    public boolean debugMmap = false;

    // Async Workers (Phase 3)
    public boolean asyncWorkersEnabled = true;
    public int chunkParserThreads = 8;
    public int chunkParserMaxQueue = 200;
    public int chunkLoaderThreads = 4;
    public int chunkLoaderMaxQueue = 50;
    public boolean chunkPrefetchEnabled = true;
    public int chunkPrefetchRadius = 1;
    public int chunkPrefetchTicksAhead = 40;
    public int chunkPrefetchTickInterval = 5;
    public double chunkPrefetchSpeedThreshold = 0.15;
    public int chunkPrefetchMaxPerTick = 16;

    // CPU Affinity (Phase 4)
    public boolean cpuAffinityEnabled = true;
    public int[] cpuAffinityCores = {2, 3, 4, 5, 6, 7};

    // TPS Governor (Phase 5)
    public boolean tpsGovernorEnabled = true;
    public boolean adaptiveSimulationEnabled = true;
    public int simDistNormal = -1;
    public int simDistStress = 8;
    public int simDistCritical = 6;
    public double tpsStressThreshold = 15.0;
    public double tpsCriticalThreshold = 10.0;
    public boolean entityHibernationEnabled = true;
    public long hibernateMinAgeMs = 5000;
    public long hibernateStressAgeMs = 30000;
    public double stressDropMoveChance = 0.25;
    public double criticalDropMoveChance = 0.75;
    public double stressSkipAiChance = 0.25;
    public double criticalSkipAiChance = 0.75;

    public static ZeroSeekConfig load() {
        if (Files.exists(PATH)) {
            try (var reader = Files.newBufferedReader(PATH)) {
                return GSON.fromJson(reader, ZeroSeekConfig.class);
            } catch (IOException e) {
                ZeroSeekMod.LOGGER.error("Failed to load config", e);
            }
        }
        ZeroSeekConfig defaults = new ZeroSeekConfig();
        defaults.save();
        return defaults;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException e) {
            ZeroSeekMod.LOGGER.error("Failed to save config", e);
        }
    }
}
