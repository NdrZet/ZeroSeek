package com.zeroseek.io;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RebaseWorkerTest {

    @BeforeAll
    public static void setUp() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void testComputeBaseFilePath() {
        Path overworld = Path.of("world", "region_delta", "r.0.0", "c.0.0.raw");
        Path baseOverworld = RebaseWorker.computeBaseFilePath(overworld);
        assertEquals(Path.of("world", "region", "r.0.0.mca"), baseOverworld);

        Path nether = Path.of("world", "DIM-1", "region_delta", "r.-1.-1", "c.-32.-32.raw");
        Path baseNether = RebaseWorker.computeBaseFilePath(nether);
        assertEquals(Path.of("world", "DIM-1", "region", "r.-1.-1.mca"), baseNether);

        Path end = Path.of("world", "DIM1", "region_delta", "r.1.2", "c.32.64.raw");
        Path baseEnd = RebaseWorker.computeBaseFilePath(end);
        assertEquals(Path.of("world", "DIM1", "region", "r.1.2.mca"), baseEnd);

        Path custom = Path.of("world", "dimensions", "testmod", "custom_dim", "region_delta", "r.2.3", "c.64.96.raw");
        Path baseCustom = RebaseWorker.computeBaseFilePath(custom);
        assertEquals(Path.of("world", "dimensions", "testmod", "custom_dim", "region", "r.2.3.mca"), baseCustom);
    }

    @Test
    public void testDetectDimension() {
        Path overworld = Path.of("world", "region", "r.0.0.mca");
        assertEquals(ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld")),
                RebaseWorker.detectDimension(overworld));

        Path nether = Path.of("world", "DIM-1", "region", "r.-1.-1.mca");
        assertEquals(ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_nether")),
                RebaseWorker.detectDimension(nether));

        Path end = Path.of("world", "DIM1", "region", "r.1.2.mca");
        assertEquals(ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_end")),
                RebaseWorker.detectDimension(end));

        Path custom = Path.of("world", "dimensions", "testmod", "custom_dim", "region", "r.2.3.mca");
        assertEquals(ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("testmod", "custom_dim")),
                RebaseWorker.detectDimension(custom));
    }

    @Test
    public void testGroupingDeltaFiles() {
        List<Path> files = List.of(
                Path.of("world", "region_delta", "r.0.0", "c.0.0.raw"),
                Path.of("world", "region_delta", "r.0.0", "c.1.0.raw"),
                Path.of("world", "region_delta", "r.0.0", "c.5.5.raw"),
                Path.of("world", "region_delta", "r.1.1", "c.32.32.raw"),
                Path.of("world", "DIM-1", "region_delta", "r.0.0", "c.0.0.raw")
        );

        Map<Path, List<Path>> groups = new LinkedHashMap<>();
        for (Path file : files) {
            Path base = RebaseWorker.computeBaseFilePath(file);
            groups.computeIfAbsent(base, k -> new ArrayList<>()).add(file);
        }

        // Expected 3 distinct region files:
        // 1. world/region/r.0.0.mca (3 chunks)
        // 2. world/region/r.1.1.mca (1 chunk)
        // 3. world/DIM-1/region/r.0.0.mca (1 chunk)
        assertEquals(3, groups.size());
        assertEquals(3, groups.get(Path.of("world", "region", "r.0.0.mca")).size());
        assertEquals(1, groups.get(Path.of("world", "region", "r.1.1.mca")).size());
        assertEquals(1, groups.get(Path.of("world", "DIM-1", "region", "r.0.0.mca")).size());
    }

    @Test
    public void testExternalDeltaManagerScan(@TempDir Path tempDir) throws IOException {
        Path world = tempDir.resolve("world");
        Path overworldDelta = world.resolve("region_delta").resolve("r.0.0");
        Path netherDelta = world.resolve("DIM-1").resolve("region_delta").resolve("r.0.0");
        Path endDelta = world.resolve("DIM1").resolve("region_delta").resolve("r.1.1");
        Path customDelta = world.resolve("dimensions").resolve("my_mod").resolve("custom_dim").resolve("region_delta").resolve("r.2.2");
        Path vanillaRegion = world.resolve("region");

        Files.createDirectories(overworldDelta);
        Files.createDirectories(netherDelta);
        Files.createDirectories(endDelta);
        Files.createDirectories(customDelta);
        Files.createDirectories(vanillaRegion);

        Files.write(overworldDelta.resolve("c.0.0.raw"), new byte[]{1, 2, 3});
        Files.write(netherDelta.resolve("c.0.0.raw"), new byte[]{4, 5, 6});
        Files.write(endDelta.resolve("c.32.32.raw"), new byte[]{7, 8, 9});
        Files.write(customDelta.resolve("c.64.64.raw"), new byte[]{10, 11, 12});
        // This should be ignored (vanilla mca)
        Files.write(vanillaRegion.resolve("r.0.0.mca"), new byte[]{0, 0, 0});
        // Temporary delta file should be ignored
        Files.write(overworldDelta.resolve("c.1.1.raw.tmp"), new byte[]{99});

        List<Path> scanned = new ArrayList<>();
        Files.walkFileTree(world, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                if ("region".equals(name) || "poi".equals(name) || "entities".equals(name)
                        || "playerdata".equals(name) || "stats".equals(name) || "advancements".equals(name)
                        || "datapacks".equals(name)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String pathStr = file.toString().replace('\\', '/');
                if (pathStr.endsWith(".raw") && pathStr.contains("/region_delta/")) {
                    scanned.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        assertEquals(4, scanned.size());
    }
}
