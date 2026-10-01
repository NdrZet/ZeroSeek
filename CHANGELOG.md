# ZeroSeek Changelog

## [1.2.0] - 2026-10-01

### Added
- **Batch Rebase Pattern** (`RebaseWorker`):
  - Chunks written to the delta layer (`world/**/region_delta/r.X.Z/c.x.z.raw`) are now aggregated and rebased into `.mca` files in batches grouped by region coordinates.
  - Target `RegionFile` is opened once per region batch rather than opened and closed per chunk.
  - `MmapLruCache.invalidate(baseFilePath)` is called once per region batch prior to write injection.
  - Full dimension resolution support: Overworld, The Nether (`DIM-1`), The End (`DIM1`), and custom data pack dimensions (`dimensions/<namespace>/<id>`).
  - Pruned delta discovery traversal in `ExternalDeltaManager.getAllDeltaFiles()` skipping vanilla folders (`region`, `poi`, `entities`, `playerdata`, `stats`, etc.).
- **Non-Destructive TPS Governor** (`MobMixin`, `AiThrottler`):
  - Surgical mob AI decimation via MixinExtras `@WrapOperation` on `GoalSelector.tick()` and `GoalSelector.tickRunningGoals(boolean)` in `Mob.serverAiStep()`.
  - Player proximity protection: Mobs within 48 blocks of any alive player (`hasNearbyAlivePlayer`) are immune to AI throttling.
  - Distant mobs (>48 blocks) have AI goals throttled proportionally under STRESS (25%) and CRITICAL (75%) server load.
- **Unit Test Suite**:
  - JUnit 5 test coverage in `RebaseWorkerTest` verifying path calculation, dimension detection, batch grouping, and delta scanner pruning.

### Changed
- Target platform metadata strictly aligned with Fabric **1.21.11** and Java **22** (`release = 22`, class file version 66.0, `Arena.ofShared()` FFM).
- Upgraded mixin compatibility level to `JAVA_22` in `zeroseek.mixins.json`.
- Removed deprecated configuration keys (`stressDropMoveChance`, `criticalDropMoveChance`) from `ZeroSeekConfig`.

### Removed / Fixed (Safety & Stability Invariants)
- **Restored `LivingEntity.aiStep()`**: Removed `LivingEntityMixin`. Fixed entity physics corruption, freezing mobs, broken fall damage calculation, suffocation, status effect ticking, and mount passenger positioning.
- **Zero Movement Packet Dropping**: Removed `ServerGamePacketListenerImplMixin` and `MovementThrottler`. Eliminated player rubberbanding and anticheat desync kicks.
- **Zero Chunk Ticket Interference**: Removed `DistanceManagerMixin` and `ChunkTicketGovernor`. Eliminated void chunk fall traps.

---

## [1.1.0] - 2026-09-27

### Added
- **Windows MMap prefetch** via `PrefetchVirtualMemory` (FFM) in `MadviseHelper`.
  - Linux continues using `posix_madvise` (`WILLNEED` / `DONTNEED`).
  - Windows `DONTNEED` is intentionally a no-op; the OS manages page lifetime.
- **MMap LRU cache** (`MmapLruCache`):
  - Total mapped bytes are now capped by `maxMappedBytes`; eviction targets 90% of the limit to avoid bouncing.
  - Least-recently-used region files with no active readers are unmapped automatically.
  - Reference-counted `acquire` / `release` protects against eviction while a chunk is being read.
  - `MmapLruCache.invalidate(path)` removes a stale mapping; used by `RebaseWorker` before rewriting a base `.mca`.
  - All cached mappings are closed on server shutdown.

### Changed
- `README.md` fully translated to English and updated:
  - Status now lists MMap prefetch on both Windows and Linux.
  - Added the new MMap LRU cache feature.
  - Removed the old "Windows PrefetchVirtualMemory fallback" and "LRU eviction" backlog items.
  - Corrected output jar name to `zeroseek-1.1.0.jar`.
- `MmapRegionIo` debug log messages now use generic "MMap prefetch" wording.
- `ZeroSeekConfig`: added `FUTURE` comments for `maxMappedBytes` and `chunkLoaderThreads`.
- `AsyncChunkService`: added a `FUTURE` comment for the reserved `loaderPool`.
- `RegionFileMixin` now uses the global `MmapLruCache` instead of keeping a per-instance mapping.

### Removed
- Dead `AsyncChunkService.prefetchChunk(ServerLevel, ChunkPos)` method.
- Unused `RegionFileVersion` import in `RegionFileMixin`.
- Unused `WeakReference` import in `EntityHibernation`.

### Fixed
- `MadviseHelper`: corrected Linux advice constants and separated platform logic into clean Linux / Windows / no-op paths.
- `MadviseHelper`: Windows `PrefetchVirtualMemory` binding now works correctly with `MemorySegment.ofAddress(...)` for the `WIN32_MEMORY_RANGE_ENTRY` layout.

### Infrastructure
- Added this changelog file.
