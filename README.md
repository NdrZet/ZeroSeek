# ZeroSeek

High-performance MMap + Async chunk loading engine for dedicated Minecraft servers.

## What is ZeroSeek?

ZeroSeek replaces Minecraft's standard synchronous chunk loading system with a memory-mapped I/O and asynchronous processing engine. The goal is stable TPS with any player count and any movement pattern, including elytra flight and teleports.

## Current Status

Ready for use: MMap chunk reads, Delta Layer, Async Workers, CPU Affinity, TPS Governor, MMap prefetch on Windows and Linux.

## Features

- **MMap I/O Engine** — chunks are read directly from RAM via `MemorySegment` (Java 22 FFM API).
- **Delta Layer** — writes are isolated from reads; modified chunks are stored as `.raw` files with background rebase to base `.mca`.
- **Async Worker Pools** — chunk parsing (decompression + NBT + DataFixerUpper) is offloaded to dedicated fixed thread pools with bounded queues.
- **CPU Affinity** — worker threads are bound to specific cores via FFM (`sched_setaffinity` / `SetThreadAffinityMask`).
- **TPS Governor** — adaptive simulation distance, player ticket freeze/limit, entity hibernation, AI throttling, and movement packet throttling when TPS drops.
- **Entity Hibernation** — entities in "old" chunks skip ticks during STRESS/CRITICAL TPS; blocks keep ticking.
- **MMap Prefetch** — `posix_madvise` on Linux, `PrefetchVirtualMemory` on Windows.

## Planned / Not Implemented

- `TeleportGate` — lazy teleports that wait for destination chunks to be ready.
- `SpeedCap` — entity speed limiting during CRITICAL TPS.
- LRU eviction / `maxMappedBytes` enforcement.
- Async generation wrapper.
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

Output: `build/libs/zeroseek-1.0.0.jar`

## Installation

1. Copy `zeroseek-1.0.0.jar` into your server's `mods/` folder.
2. On first launch, `config/zeroseek.json` will be created — edit if needed.
3. Start the server.

## Configuration

`config/zeroseek.json` allows tuning:

- `mmapEnabled` / `deltaLayerEnabled` — enable MMap and Delta Layer.
- `maxMappedBytes` — MMap memory budget (LRU eviction is not yet implemented).
- `rebaseIntervalSeconds` — background rebase interval.
- `chunkParserThreads` / `chunkLoaderThreads` — pool sizes.
- `cpuAffinityEnabled` / `cpuAffinityCores` — core binding.
- `tpsGovernorEnabled`, `simDistNormal/Stress/Critical`, `tpsStress/CriticalThreshold` — TPS governor.
- `entityHibernationEnabled`, `hibernateMinAgeMs`, `hibernateStressAgeMs` — entity hibernation.

## Compatibility

- **Tested on Windows:** affinity, mmap, delta, rebase, TPS governor, and PrefetchVirtualMemory work.
- **Linux:** affinity + madvise are implemented but not battle-tested.
- **Likely compatible:** Terralith, Biomes O' Plenty, Lithium, Starlight, Voxy, Xaero's World Map, Pl3xMap.
- **Conflict:** C2ME — auto-detect is not implemented yet. If you use C2ME, set `mmapEnabled` to `false`.
