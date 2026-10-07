# ZeroSeek

High-performance MMap + Async chunk loading and Concurrent World Generation engine for dedicated Minecraft servers.

## What is ZeroSeek?

ZeroSeek replaces Minecraft's standard synchronous chunk loading and single-threaded world generation with a memory-mapped I/O, asynchronous processing, and 2D spatial locking engine. The goal is stable TPS with any player count and any movement pattern, including elytra flight and chunk pre-generation.

## Current Status

Ready for use: MMap chunk reads, Delta Layer with Batch Rebase, Async Workers, Concurrent World Generation Engine, CPU Affinity, Safe TPS Governor, MMap prefetch on Windows and Linux.

## Features

- **Modular Multi-Project Architecture** — ZeroSeek is built as an extensible multi-module engine using Fabric's Jar-in-Jar system:
  - `zeroseek-base`: core MMap chunk I/O, async worker pools, CPU affinity, and Safe TPS governor.
  - `zeroseek-worldgen`: 2D spatial locking, concurrent world generation pipeline, and End biome caching.
  - `zeroseek-cuda`: hardware-accelerated world generation using NVIDIA CUDA with Java 22 FFM API, NVRTC runtime compilation, and Mixed Precision (FP64 Base / FP32 Local).
  - Submodules can be included or excluded at build time, and are packaged into a single `zeroseek-1.4.0.jar` for server administrators.
- **NVIDIA CUDA GPU Acceleration (`zeroseek-cuda`)**:
  - Direct Driver API binding (`nvcuda.dll` / `libcuda.so`) and NVRTC runtime compiler (`nvrtc64_*.dll`) via Java 22 FFM API with zero external C++ daemons or JNI dependencies.
  - **Mixed Precision Engine**: Global coordinates and offsets remain in high-precision `double` (FP64) to prevent precision drift at high distances, while local octave evaluation executes in pure `float` (FP32), saturating modern RTX CUDA cores (15.1+ TFLOPS on RTX 4060).
  - **Asynchronous Chunk Batch Dispatcher**: Batches multi-chunk noise density queries (`CudaChunkBatchDispatcher`) to eliminate kernel launch and DMA overhead.
  - **Zero-Friction Fallback**: Transparently falls back to CPU world generation if CUDA is unavailable or disabled.
- **Concurrent World Generation Engine** — multithreaded worldgen pipeline under Minecraft 1.21.11 (Official Mojang Mappings):
  - **2D Spatial Locking** — fine-grained Chebyshev grid ($3 \times 3$ for features with write radius 1) using non-blocking `CompletableFuture` dependency chaining. Strict canonical coordinate sorting (`Arrays.sort(positions)`) mathematically eliminates cyclic wait-for deadlocks.
  - **Dedicated Generator Worker Pool** — world generation task dispatching in `ChunkMap` is redirected to an isolated fixed thread pool with hardware CPU Affinity bound to cores 2–7 via Java 22 FFM API.
  - **Pipeline Mixin Injections** — `ChunkStep.apply` wraps chunk status tasks to enforce spatial isolation without blocking worker threads.
  - **Zero-Bouncing Noise Dispatch** — nested `CompletableFuture.supplyAsync` bouncing in `NoiseBasedChunkGenerator` (`fillFromNoise`, `createBiomes`) is routed directly into the dedicated generator worker pool with CPU affinity, preventing thread starvation.
  - **TheEndBiomeSource 2D LRU Cache** — samples in The End (which strictly depend on 2D coordinates) are cached in a per-thread LRU map (`Long2ObjectLinkedOpenHashMap`), skipping redundant Simplex/Perlin noise computations.
  - **TPS Governor Adaptive Backpressure** — when the server enters `CRITICAL` TPS state, heavy boundary generation tasks (`radius > 0`, such as `FEATURES`) are dynamically throttled to at most 2 concurrent tasks via a non-blocking permit queue. Crucially, permits are queued without holding spatial locks, preventing permit-coordinate deadlocks. When TPS recovers to `NORMAL`/`STRESS`, permits are immediately reset and generation resumes at full speed.
- **MMap I/O Engine** — chunks are read directly from RAM via `MemorySegment` (Java 22 FFM API).
- **Delta Layer & Batch Rebase** — writes are isolated from reads; modified chunks are stored as `.raw` files. Background rebase groups delta chunks by region coordinates (`pos.getRegionX()`, `pos.getRegionZ()`), invalidates the MMap cache once per region, and opens each target `.mca` `RegionFile` only once per batch, dramatically reducing I/O and lock overhead.
- **Async Worker Pools** — chunk parsing (decompression + NBT + DataFixerUpper) is offloaded to dedicated fixed thread pools with bounded queues.
- **CPU Affinity** — worker threads are bound to specific cores via FFM (`sched_setaffinity` / `SetThreadAffinityMask`).
- **Safe TPS Governor** — non-destructive, physics-safe performance preservation:
  - Dynamically lowers simulation distance during STRESS/CRITICAL TPS.
  - Safe `GoalSelector` throttling: skips goal execution only for mobs further than 48 blocks away from alive players (`hasNearbyAlivePlayer`).
  - Destructive hacks have been completely removed: `LivingEntity.aiStep()` is never cancelled (preserving gravity, physics, fluid drag, potion ticks, and passenger alignment), movement packets (`ServerboundMovePlayerPacket`) are never dropped (eliminating client rubberbanding), and chunk tickets (`DistanceManager`) are never frozen.
- **Entity Hibernation** — entities in "old" chunks skip ticks during STRESS/CRITICAL TPS; blocks keep ticking.
- **MMap Prefetch** — `posix_madvise` on Linux, `PrefetchVirtualMemory` on Windows.
- **MMap LRU Cache** — total mapped bytes are capped by `maxMappedBytes`; least-recently-used region files are unmapped automatically.

## Planned / Not Implemented

- `TeleportGate` — lazy teleports that wait for destination chunks to be ready.
- `SpeedCap` — entity speed limiting during CRITICAL TPS.
- Auto-detect C2ME and disable mmap automatically.

## Target Platform

- Minecraft **1.21.11**
- Fabric Loader **0.18.2+**
- Java **22** (required for FFM / MemorySegment)
- Server-side only

## Build

```bash
./gradlew build
```

Output: `build/libs/zeroseek-1.4.0.jar`

## Installation

1. Copy `zeroseek-1.4.0.jar` into your server's `mods/` folder.
2. On first launch, `config/zeroseek.json` will be created — edit if needed.
3. Start the server.

## Configuration

`config/zeroseek.json` allows tuning:

- `concurrentWorldGenEnabled` — toggle concurrent world generation engine (defaults to `true`).
- `chunkGeneratorThreads` — dedicated worker pool size for world generation (defaults to `8`).
- `worldGenAffinityCores` — CPU cores assigned to world generation workers (defaults to `[2, 3, 4, 5, 6, 7]`).
- `cudaAccelerationEnabled` — enable NVIDIA CUDA GPU acceleration (defaults to `true`).
- `cudaBatchSize` — batch size of chunks submitted to GPU per kernel launch (defaults to `16`).
- `cudaMaxQueue` — maximum capacity of the pending chunk queue (defaults to `1024`).
- `nvrtcLibraryPath` — custom path to `nvrtc64_*.dll` if not auto-detected (optional).
- `mmapEnabled` / `deltaLayerEnabled` — enable MMap and Delta Layer.
- `maxMappedBytes` — MMap memory budget.
- `rebaseIntervalSeconds` — background rebase interval.
- `chunkParserThreads` / `chunkLoaderThreads` — pool sizes for parsing and loading.
- `cpuAffinityEnabled` / `cpuAffinityCores` — worker core binding.
- `tpsGovernorEnabled`, `simDistNormal/Stress/Critical`, `tpsStress/CriticalThreshold` — TPS governor settings.
- `entityHibernationEnabled`, `hibernateMinAgeMs`, `hibernateStressAgeMs` — entity hibernation.

## Compatibility

- **Tested on Windows:** affinity, mmap, delta, batch rebase, safe TPS governor, concurrent worldgen, and PrefetchVirtualMemory work.
- **Linux:** affinity + madvise are implemented but not battle-tested.
- **Likely compatible:** Terralith, Biomes O' Plenty, Lithium, Starlight, Voxy, Xaero's World Map, Pl3xMap.
- **Conflict:** C2ME — auto-detect is not implemented yet. If you use C2ME, set `mmapEnabled` and `concurrentWorldGenEnabled` to `false`.
