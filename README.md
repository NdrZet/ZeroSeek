# ZeroSeek

High-performance MMap + Async chunk loading engine for dedicated Minecraft servers.

## What is ZeroSeek?

ZeroSeek replaces Minecraft's standard synchronous chunk loading system with a memory-mapped I/O and asynchronous processing engine. The goal is stable TPS with any player count and any movement pattern, including elytra flight and teleports.

## Current Status

Ready for use: MMap chunk reads, Delta Layer with Batch Rebase, Async Workers, CPU Affinity, Safe TPS Governor, MMap prefetch on Windows and Linux.

## Features

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

Output: `build/libs/zeroseek-1.2.0.jar`

## Installation

1. Copy `zeroseek-1.2.0.jar` into your server's `mods/` folder.
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

- **Tested on Windows:** affinity, mmap, delta, batch rebase, safe TPS governor, and PrefetchVirtualMemory work.
- **Linux:** affinity + madvise are implemented but not battle-tested.
- **Likely compatible:** Terralith, Biomes O' Plenty, Lithium, Starlight, Voxy, Xaero's World Map, Pl3xMap.
- **Conflict:** C2ME — auto-detect is not implemented yet. If you use C2ME, set `mmapEnabled` to `false`.
