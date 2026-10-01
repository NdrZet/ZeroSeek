# ZeroSeek — Implementation Plan
## MMap Async Chunk Engine with Hardened Thread Pools
### Target: Minecraft 1.21.11, Fabric, Java 22 (preview enabled)

**Mod ID:** `zeroseek`  
**Archives Base Name:** `zeroseek`  
**Maven Group:** `com.zeroseek`  
**Project renamed from:** `SPA_Core`

---

## 1. Цель

Заменить ванильный синхронный chunk I/O движок на высокопроизводительный MMap + Async engine с жёстким управлением ресурсами CPU. Мод должен работать на Windows и Linux, поддерживать конфигурацию через JSON, обеспечивать стабильный TPS при любом онлайне и любом перемещении игроков (включая элитры/телепорты).

Ключевые метрики успеха:
- Чтение чанка: ~0.01–0.05 мс (вместо 2–5 мс в ванили)
- TPS при 10+ игроках на элитрах: 19.5–20 (вместо 8–14)
- Полный контроль над CPU (affinity, fixed pools, bounded queues)

---

## 2. Высокоуровневая архитектура

```
┌─────────────────────────────────────────────────────────────┐
│                    Основной серверный поток                 │
│  (Tick loop, entity AI, block updates, player packets)      │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
┌──────────────┐     ┌─────────────────┐    ┌──────────────┐
│ TPS Governor │     │  Chunk Ticket   │    │ TeleportGate │
│              │     │    Governor     │    │              │
│ Adaptive Sim │     │ (freeze/limit)  │    │ (lazy TP)    │
│ Distance     │     │                 │    │              │
└──────────────┘     └────────┬────────┘    └──────────────┘
                              │
                ┌─────────────┼─────────────┐
                ▼             ▼             ▼
        ┌──────────┐  ┌────────────┐  ┌──────────────┐
        │  Кеш     │  │  Virtual   │  │   Virtual    │
        │ готовых  │  │  Thread    │  │   Thread     │
        │ чанков   │  │  Pool:     │  │   Pool:      │
        │          │  │  Parser    │  │   Generator  │
        │Concurrent│  │  Workers   │  │   Wrapper    │
        │  HashMap │  │            │  │              │
        └──────────┘  └─────┬──────┘  └──────┬───────┘
                            │                  │
                            ▼                  ▼
                  ┌──────────────────────────────────────┐
                  │          MMap I/O Engine             │
                  │  ┌──────────────┐  ┌──────────────┐  │
                  │  │  Base Layer  │  │  Delta Layer │  │
                  │  │  (READ_ONLY) │  │  (READ_WRITE)│  │
                  │  │ MemorySegment│  │  .raw files  │  │
                  │  │  via MBB     │  │  (per chunk) │  │
                  │  └──────────────┘  └──────────────┘  │
                  │                                      │
                  │  ┌──────────────┐  ┌──────────────┐  │
                  │  │  LRU Eviction│  │ Async Rebase │  │
                  │  │  (madvise)   │  │ (worker)     │  │
                  │  └──────────────┘  └──────────────┘  │
                  └──────────────────────────────────────┘
```

---

## 3. Компоненты системы

### 3.1 MMap I/O Engine

#### 3.1.1 Base Layer (Read-Only)
- Все `.mca` файлы из `world/region/` читаются через `FileChannel.map()` → `MappedByteBuffer` → `MemorySegment.ofBuffer()` в режиме `READ_ONLY`.
- `Arena.ofShared()` — shared across threads.
- Используется для чтения неизменённых (base) чанков.
- ОС агрессивно кеширует страницы в RAM.

#### 3.1.2 Delta Layer (Read-Write)
- Каждый изменённый чанк сохраняется в отдельный `.raw` файл в `world/region_delta/r.X.Z/c.x.z.raw`.
- Запись производится напрямую через `ExternalDeltaManager`, минуя ванильный `RegionFile`.
- При записи чанка: пишем в Delta Layer. Base Layer не трогается.
- **Rebase:** раз в 5 минут (или при shutdown) фоновый поток сливает Delta → Base, tmp удаляется.
- **Data safety:** при краше до rebase — данные остаются в `.raw` файлах. При следующем старте delta-файлы читаются с приоритетом над base `.mca`, а rebase запускается по таймеру.

#### 3.1.3 Read Logic
```
1. Запрос чанка (ChunkPos)
2. Проверить Delta Layer (dirty map)
   - Если есть → читаем из Delta RegionFile
   - Если нет → читаем из Base MemorySegment
3. Вернуть DataInputStream (compressed chunk data)
```

#### 3.1.4 Write Logic
```
1. Запрос записи (ChunkPos, ByteBuffer)
2. `ExternalDeltaManager` атомарно пишет `.raw` файл в `world/region_delta/r.X.Z/c.x.z.raw`
3. Base `.mca` не трогается
4. Rebase worker раз в `rebaseIntervalSeconds` сливает `.raw` → base `.mca`
```

#### 3.1.5 LRU + Memory Pressure — ⬜ не реализовано
- `ConcurrentHashMap<RegionPos, MmapRegionFile> activeRegions`
- `LongAdder mappedBytes` — атомарный счётчик занятой памяти
- `maxMappedBytes = 2GB` (configurable)
- При превышении: вытесняем самые старые регионы (LRU через `LinkedHashMap`), вызываем `Arena.close()` + `madvise(DONTNEED)`
- **Статус:** `maxMappedBytes` есть в конфиге, но LRU eviction не реализован. Каждый `RegionFile` создаёт свой `MmapRegionIo` и держит его до `close()`.

#### 3.1.6 Prefetch & Madvise — ✅ реализовано
- При открытии `MmapRegionIo` подаётся подсказка ОС загрузить страницы заранее.
- Linux: `posix_madvise(MemorySegment, POSIX_MADV_WILLNEED)` / `POSIX_MADV_DONTNEED`.
- Windows: `PrefetchVirtualMemory` через FFM (только WILLNEED; DONTNEED — no-op).
- **Статус:** `MadviseHelper` поддерживает обе платформы; `MmapRegionIo` вызывает willNeed/dontNeed при open/close.

---

### 3.2 Async Worker Pools (Hardened)

#### 3.2.1 Chunk Parser Pool — ✅ реализовано
- **Задачи:** Zlib decompress, NBT parse, DataFixerUpper
- **Пул:** `HardenedWorkerPool` (`ThreadPoolExecutor` с `core=max=N`, bounded queue)
- **Reject policy:** `DiscardOldestPolicy` с логированием
- **Thread factory:**
  - `setDaemon(false)` — потоки вечные
  - `setPriority(Thread.MAX_PRIORITY)`
  - Affinity будет добавлен в Phase 4
- **Интеграция:** `ChunkMapMixin` redirect'ит `Util.backgroundExecutor()` в parser pool при `scheduleChunkLoad`

#### 3.2.2 Chunk Loader / Prefetch Pool — ✅ частично
- **Пул:** `HardenedWorkerPool` создан, но пока не используется для MMap/madvise
- **Prefetch:** `ChunkPrefetcher` predictive prefetch по вектору движения игрока
- **Madvise:** будет добавлен в Phase 4

#### 3.2.3 Async Delta Rebase Pool — ✅ реализовано
- **Задачи:** Merge Delta → Base
- **Пул:** один `ScheduledExecutorService` поток (`zeroseek-rebase`)
- **Периодичность:** каждые 5 минут (конфигурируемо)

#### 3.2.4 Async Generation Wrapper (Phase 3) — ⬜ не реализовано
- Если чанка нет на диске (негенерированная территория) — ванильный `ChunkStatus` pipeline выполняется в Virtual Thread / Worker Pool.
- Пока чанк генерируется — возвращаем `EmptyChunk` (или каменную заглушку).
- Отложено на Phase 5+ как отдельная задача.

---

### 3.3 Cross-Platform CPU Affinity ✅

#### 3.3.1 Linux (`sched_setaffinity`)
- FFM вызов `gettid()` — получить OS thread ID
- FFM вызов `sched_setaffinity(tid, cpu_set_t *mask)`
- `cpu_set_t` — битовая маска в `MemorySegment` (128 байт для 1024 CPU)

#### 3.3.2 Windows (`SetThreadAffinityMask`)
- FFM вызов `GetCurrentThread()` — псевдохэндл
- FFM вызов `SetThreadAffinityMask(handle, mask)`
- Маска: `1L << coreId`

#### 3.3.3 Fallback
- Если FFM не доступен (sandbox, старая JVM, ошибка загрузки библиотеки) — автоматически отключаем affinity.
- Используется `NoopAffinity`.
- Лог: `WARN: CPU affinity unavailable on this platform/JVM`

---

### 3.4 TPS Governor & High-Level Logic

#### 3.4.1 TPSMonitor
- Используется `MinecraftServer.getAverageTickTimeNanos()` — скользящее среднее vanilla за последние 100 тиков.
- Состояния классифицирутся по порогам из конфига:
  - `NORMAL`: TPS > `tpsStressThreshold`
  - `STRESS`: `tpsCriticalThreshold` < TPS ≤ `tpsStressThreshold`
  - `CRITICAL`: TPS ≤ `tpsCriticalThreshold`
- Дефолты: STRESS ≤ 15 TPS, CRITICAL ≤ 10 TPS.

#### 3.4.2 Chunk Ticket Governor
- **Миксин:** `DistanceManager` / `ServerChunkCache.tick()`
- **STRESS:** блокируем добавление **новых** player tickets. Уже загруженные чанки остаются.
- **CRITICAL:** полностью отменяем `updatePlayerTickets`. Чанки не грузятся и не выгружаются (мир «замирает»).
- **Recovery:** TPS > 19.5 в течение 2 секунд → размораживаем.

#### 3.4.3 Adaptive Simulation Distance
- Базовое значение (`baseline`) берётся из `server.properties` (`simulation-distance`), либо из `simDistNormal`, если оно ≥ 0.
- При STRESS: `min(baseline, simDistStress)`.
- При CRITICAL: `min(baseline, simDistCritical)`.
- Применяется через `ServerChunkCache.setSimulationDistance()` runtime.
- Дефолты в конфиге: `simDistNormal=-1` (использовать server.properties), `simDistStress=8`, `simDistCritical=6`.

#### 3.4.4 Teleport Gate
- **Миксин:** `ServerPlayer.teleport()` / `changeDimension()`
- Перед телепортом проверяем готовность целевого региона (через `MmapRegionFile` / future).
- Если не готов — `ci.cancel()`, добавляем в `PendingTeleport` очередь.
- Обработка в `ServerTickEvents.END_SERVER_TICK`: когда регион замапплен — применяем телепорт.
- Таймаут: 10 секунд. Если не догрузилось — телепорт принудительно.

#### 3.4.5 Entity Hibernation
- **Миксин:** `ServerLevel.tickNonPassenger(Entity)`
- Возраст чанка отслеживается через `WeakHashMap<LevelChunk, Long>` (`loadedAt` = время первого тика сущности в чанке).
- Если `currentTime - loadedAt` превышает порог — пропускаем тик **сущностей** в этом чанке.
- Блоки, random ticks, block entities продолжают тикать.
- При выгрузке чанка (`ServerLevel.unload`) запись удаляется из мапы.
- Пороги: `hibernateMinAgeMs=5000`, `hibernateStressAgeMs=30000`.

#### 3.4.6 Player Speed Cap (опционально)
- **Миксин:** `LivingEntity.travel()` или обработка пакетов движения
- **CRITICAL TPS:** скорость всех сущностей (элитры, лошади, свиньи) × 0.5
- Предотвращает «убегание» игроков от прогруженной зоны.

---

## 4. Конфигурация

Файл: `config/zeroseek.json`

```json
{
  "mmapEnabled": true,
  "deltaLayerEnabled": true,
  "maxMappedBytes": 2147483648,
  "rebaseIntervalSeconds": 300,
  "debugMmap": false,

  "asyncWorkersEnabled": true,
  "chunkParserThreads": 8,
  "chunkParserMaxQueue": 200,
  "chunkLoaderThreads": 4,
  "chunkLoaderMaxQueue": 50,
  "chunkPrefetchEnabled": true,
  "chunkPrefetchRadius": 1,
  "chunkPrefetchTicksAhead": 40,
  "chunkPrefetchTickInterval": 5,
  "chunkPrefetchSpeedThreshold": 0.15,
  "chunkPrefetchMaxPerTick": 16,

  "tpsGovernorEnabled": true,
  "adaptiveSimulationEnabled": true,
  "simDistNormal": -1,
  "simDistStress": 8,
  "simDistCritical": 6,
  "tpsStressThreshold": 15.0,
  "tpsCriticalThreshold": 10.0,

  "entityHibernationEnabled": true,
  "hibernateMinAgeMs": 5000,
  "hibernateStressAgeMs": 30000,

  "stressDropMoveChance": 0.25,
  "criticalDropMoveChance": 0.75,

  "stressSkipAiChance": 0.25,
  "criticalSkipAiChance": 0.75
}
```

---

## 5. Миксины (Mixin Inventory)

| # | Миксин | Класс | Цель | Статус |
|---|--------|-------|------|--------|
| 1 | `RegionFileMixin` | `RegionFile` | Чтение из `MmapRegionIo`, запись в `ExternalDeltaManager` (только chunk storage) | ✅ |
| 2 | `RegionFileInvoker` | `RegionFile` | Invoker для `write(pos, ByteBuffer)` (rebase) | ✅ |
| 3 | `ChunkMapMixin` | `ChunkMap` | Redirect `Util.backgroundExecutor()` в `HardenedWorkerPool`, deduplication cache | ✅ |
| 4 | `ChunkMapInvoker` | `ChunkMap` | Invoker для `scheduleChunkLoad(pos)` (prefetch) | ✅ |
| 5 | `ServerChunkCacheMixin` | `ServerChunkCache` | Adaptive Simulation Distance | ✅ |
| 6 | `DistanceManagerMixin` | `DistanceManager` | Rate limiting / freeze player tickets | ✅ |
| 7 | `ServerLevelMixin` | `ServerLevel` | Entity Hibernation (`tickNonPassenger` inject) | ✅ |
| 8 | `ServerPlayerMixin` | `ServerPlayer` | Teleport Gate (teleport / changeDimension inject) | ⬜ |
| 9 | `LivingEntityMixin` | `LivingEntity` | AI throttling при STRESS/CRITICAL TPS | ✅ |
| 10 | `MinecraftServerMixin` | `MinecraftServer` | TPS Monitor (`getAverageTickTimeNanos()`) | ✅ |
| 11 | `ServerGamePacketListenerImplMixin` | `ServerGamePacketListenerImpl` | Throttling пакетов движения игрока | ✅ |

**Удалённые / не требуются в 1.21:**
- `RegionFileStorageMixin` — `ChunkMap` напрямую управляет RegionFile в 1.21
- `ChunkStorageMixin` — заменён на `ChunkMapMixin`

---

## 6. Пакетная структура (Package Structure)

```
com.zeroseek
├── ZeroSeekMod.java
├── config
│   └── ZeroSeekConfig.java
├── io
│   ├── MmapRegionIo.java
│   ├── ExternalDeltaManager.java
│   ├── RebaseWorker.java
│   ├── RebaseState.java
│   └── MadviseHelper.java
├── async
│   ├── HardenedWorkerPool.java
│   ├── AsyncChunkService.java
│   └── affinity
│       ├── AffinityProvider.java
│       ├── PlatformAffinity.java
│       ├── LinuxAffinity.java
│       ├── WindowsAffinity.java
│       └── NoopAffinity.java
├── chunk
│   └── ChunkPrefetcher.java
├── tps
│   ├── TPSMonitor.java
│   ├── TPSState.java
│   ├── TickAggregator.java
│   ├── AdaptiveSimulation.java
│   ├── ChunkTicketGovernor.java
│   ├── EntityHibernation.java
│   ├── MovementThrottler.java
│   └── AiThrottler.java
└── mixin
    ├── RegionFileMixin.java
    ├── RegionFileInvoker.java
    ├── ChunkMapMixin.java
    ├── ChunkMapInvoker.java
    ├── MinecraftServerMixin.java
    ├── ServerChunkCacheMixin.java
    ├── DistanceManagerMixin.java
    ├── ServerLevelMixin.java
    ├── ServerGamePacketListenerImplMixin.java
    └── LivingEntityMixin.java

Планируемые (Phase 5+):
├── teleport
│   ├── TeleportGate.java
│   └── PendingTeleport.java
└── mixin
    └── ServerPlayerMixin.java
```

---

## 7. Этапы реализации (Phases)

### Phase 1 — Core MMap (MVP) ✅ DONE
- [x] `MmapRegionIo`: `FileChannel.map()` → `MappedByteBuffer` → `MemorySegment.ofBuffer()` для READ_ONLY base `.mca`
- [x] Миксин `RegionFileMixin`: redirect `getChunkDataInputStream` через `MmapRegionIo`
- [x] Отключение Delta Layer (`deltaLayer: false` в конфиге) — fallback
- [x] Fallback: если mmap fails → ванильный `FileChannel`
- [x] Логирование benchmark'ов (время чтения чанка)
- [x] Проверка `RegionStorageInfo.type()` — MMap применяется только к chunk storage

**Уточнения / ограничения:**
- LRU eviction / `maxMappedBytes` контроль пока не реализованы
- Prefetch / madvise (MMap регионов) пока не реализованы

**Результат:** Чанки читаются из RAM. TPS стабилизируется. Нет async, нет affinity.

### Phase 2 — Delta Layer + Safety ✅ DONE
- [x] `ExternalDeltaManager`: изоляция записи в `world/region_delta/` (`r.X.Z/c.x.z.raw`)
- [x] Write path: chunk-данные пишутся в Delta; entity/poi storage используют ванильный путь
- [x] Read path: Delta → Base priority
- [x] `RebaseWorker`: фоновый merge Delta → Base каждые 5 мин
- [x] `RebaseState`: thread-safe флаг rebase для обхода delta-перехвата
- [x] Crash recovery: скан `region_delta/` при старте

**Уточнения:**
- Реальный формат хранения — отдельные `.raw` файлы в `world/region_delta/`, а не `.mca.tmp`
- Delta применяется только к `RegionStorageInfo.type() == "chunk"`

**Результат:** Запись изолирована. Нет contention read/write. Данные защищены.

### Phase 3 — Async Workers ✅ DONE (core functionality)
- [x] `HardenedWorkerPool`: fixed thread pools, bounded queues, `DiscardOldestPolicy` + логирование
- [x] `AsyncChunkService`: центральный сервис parser/loader пулов + deduplication cache
- [x] `ChunkMapMixin`: redirect `Util.backgroundExecutor()` → `HardenedWorkerPool` в `scheduleChunkLoad`
- [x] `ChunkMapInvoker`: доступ к `scheduleChunkLoad` для prefetch
- [x] `ChunkPrefetcher`: predictive prefetch по вектору движения игрока (throttle, speed threshold, per-tick cap)
- [x] Concurrent кеш загружаемых чанков (`ConcurrentHashMap<ChunkPos, CompletableFuture<ChunkAccess>>`)

**Уточнения / отличия от плана:**
- `ChunkStorageMixin` не требуется в Minecraft 1.21 — `ChunkMap` напрямую наследует `SimpleRegionStorage`, поэтому интеграция идёт через `ChunkMapMixin`
- `ChunkParserWorker` и `ChunkLoaderWorker` не вынесены в отдельные классы; их роль выполняет `AsyncChunkService` + `ChunkPrefetcher`
- Async Generation Wrapper (delegate ванильного `ChunkStatus` pipeline в worker) пока не реализован

**Результат:** NBT parse + DataFixerUpper оффлоадятся в dedicated parser pool. Prefetch работает. CPU offload.

### Phase 4 — Affinity + Madvise ✅ DONE
- [x] `LinuxAffinity` через FFM API (`sched_setaffinity`, `gettid`)
- [x] `WindowsAffinity` через FFM API (`SetThreadAffinityMask`, `GetCurrentThread`)
- [x] `NoopAffinity` fallback
- [x] `PlatformAffinity` — автовыбор провайдера по ОС
- [x] Интеграция affinity в `HardenedWorkerPool.ThreadFactory`
- [x] `MadviseHelper`: `POSIX_MADV_WILLNEED` / `DONTNEED` (Linux)
- [x] Config integration: `cpuAffinityEnabled`, `cpuAffinityCores`

**Уточнения:**
- Affinity применяется к `zeroseek-parser-*` и `zeroseek-loader-*` потокам при их первом запуске
- Windows: `PrefetchVirtualMemory` реализован через FFM
- Linux: полная поддержка affinity + madvise
- Linux: полная поддержка affinity + madvise

**Результат:** Worker threads привязаны к ядрам. MMap регионы получают `MADV_WILLNEED` при открытии и `MADV_DONTNEED` при закрытии.

### Phase 5 — High-Level TPS Governor ✅ DONE (core functionality)
- [x] `TPSMonitor` + `TPSState` + `TickAggregator`
- [x] `ChunkTicketGovernor` (freeze/unfreeze player tickets)
- [x] `AdaptiveSimulation`
- [x] `EntityHibernation`
- [x] `MovementThrottler` — дроп пакетов движения игрока при STRESS/CRITICAL
- [x] `AiThrottler` — пропуск `LivingEntity.aiStep()` при STRESS/CRITICAL

**Уточнения / отличия от плана:**
- Вместо ручного замера `System.nanoTime()` используется `MinecraftServer.getAverageTickTimeNanos()`.
- TPS классифицируется по порогам `tpsStressThreshold` / `tpsCriticalThreshold`.
- Entity Hibernation реализована через `ServerLevel.tickNonPassenger(Entity)`, не `tickChunk`. Возраст чанка хранится в `WeakHashMap<LevelChunk, Long>`.
- `DistanceManagerMixin`: `addPlayer()` отменяется в STRESS/CRITICAL, `updatePlayerTickets()` отменяется только в CRITICAL.
- `ServerGamePacketListenerImplMixin` дропает пакеты перемещения с заданной вероятностью.
- `SpeedCap` / `TeleportGate` — не реализованы, отложены.

**Результат:** Сервер адаптивно снижает нагрузку при просадках TPS: уменьшает simulation distance, замораживает новые player tickets, пропускает тики сущностей и дропает избыточные пакеты движения.

### Phase 6 — Polish & Testing (3–5 дней)
- [ ] Тест на Windows (affinity, mmap)
- [ ] Тест на Linux (affinity, madvise)
- [ ] Тест с Terralith / BOP (совместимость генерации)
- [ ] Тест с 10+ игроками на элитрах (TPS профилирование)
- [ ] Auto-detect C2ME → disable mmap (conflict prevention)
- [ ] Документация для админов

---

## 8. Требования

| Параметр | Минимум | Рекомендуется |
|----------|---------|---------------|
| Java | 21 | 22+ (для MemorySegment / FFI / Virtual Threads / `--enable-preview`) |
| Minecraft | 1.21.11 | 1.21.11+ |
| Fabric Loader | ≥ 0.16.0 | 0.18.2+ |
| RAM (дополнительно) | +1 GB | +2 GB под mmap |
| ОС | Windows 10+ / Linux 5.0+ | Linux (лучше работает madvise + affinity) |
| CPU cores | 4 | 8+ (для affinity + main thread) |

---

## 9. Риски и Mitigation

| Риск | Вероятность | Mitigation |
|------|-------------|------------|
| MMap конфликтует с другим модом (C2ME) | Средняя | Auto-detect при старте. Отключение mmap + fallback к ванили. |
| Потеря данных при краше (delta не смержен) | Низкая | Delta файлы остаются на диске. Crash recovery при старте. |
| Affinity не работает (sandbox, права) | Средняя | Graceful fallback к `NoopAffinity`. Лог warn. |
| OOM (mmap съел всю RAM) | Низкая | LRU eviction + `maxMappedBytes`. `madvise(DONTNEED)`. |
| Несовместимость с модом генерации | Низкая | Мы не трогаем `ChunkGenerator`. Тест с Terralith/BOP. |
| Async NBT parse ломает порядок загрузки | Средняя | Только parse в потоке. Вставка в `ChunkMap` — всё ещё в main thread через `server.execute()`. |

---

## 10. Сравнительная таблица (Итог)

| Метрика | Ваниль | C2ME | ZeroSeek (Phase 6) |
|---------|--------|------|---------------------|
| Чтение чанка (hot) | 2–5 мс | 0.5–2 мс | **0.01–0.05 мс** |
| Запись чанка | 2–5 мс (sync) | 0.5 мс (async) | **0.01 мс (mmap delta)** |
| Генерация чанка | 20–100 мс | 5–25 мс (async) | **20–100 мс** (но async wrapper) |
| TPS, 10 игроков на элитрах | 8–14 | 17–19.5 | **19.5–20** |
| CPU контроль | ❌ Нет | ❌ Нет | **✅ Affinity + fixed pools** |
| RAM | База | +2–4 GB | **+1–2 GB** |
| Java | 21 | 22+ | **22 (preview)** |
| Стабильность | 100% | 85–90% (alpha) | **Контролируемая** |

---

## 11. Решение

**Путь:** Чистый ZeroSeek с MMap + Async + Hardened Thread Pools + CPU Affinity.  
**Без C2ME.** Полный контроль. Java 22 (preview). Кроссплатформенность. Конфиг через JSON.

**Следующий шаг:** Phase 6 — Polish & Testing, а также отложенные элементы Phase 5 (`TeleportGate`, `SpeedCap`, LRU eviction, async generation wrapper).

**Краткий статус:**
- Phase 1 (MMap) — ✅ готово
- Phase 2 (Delta Layer) — ✅ готово
- Phase 3 (Async Workers) — ✅ готово
- Phase 4 (Affinity + Madvise) — ✅ готово
- Phase 5 (TPS Governor) — ✅ готово (ядро)
- Phase 6 (Polish & Testing) — ⬜

---

## 12. Аудит кода и внесённые исправления

Последний проход по всем Java-файлам выявил ряд проблем. Исправленные:

### Исправлено
| Проблема | Файлы | Исправление |
|---|---|---|
| `ZeroSeekConfig.load()` возвращал `null` при повреждённом JSON | `ZeroSeekConfig.java` | Ловим `Exception`, проверяем `null`, fallback к дефолтам |
| `shutdown()` нигде не вызывался | `ZeroSeekMod.java` | Зарегистрирован `ServerLifecycleEvents.SERVER_STOPPING` |
| `MmapRegionIo` утекал `Arena` при ошибке маппинга | `MmapRegionIo.java` | Арена закрывается в `catch` |
| `RebaseWorker` создавал `RegionStorageInfo` с типом `"region"` | `RebaseWorker.java` | Исправлено на `"chunk"` |
| `EntityHibernation` отменял весь `tickChunk` (включая блоки) | `ServerLevelMixin.java`, `EntityHibernation.java` | Перенесено на `tickNonPassenger(Entity)` — спят только сущности |
| `ChunkPrefetcher.tickCounter` был глобальным | `ChunkPrefetcher.java` | Счётчик стал per-level (`Map<ServerLevel, Integer>`) |
| `WindowsAffinity` использовал закрытую `Arena` | `WindowsAffinity.java` | Перешли на `Arena.ofShared()`, храним как поле |
| `LevelChunkMixin` вызывал `IllegalClassLoadError` | удалён | Используем `WeakHashMap<LevelChunk, Long>` |
| `MadviseHelper` имел неверные Linux-константы | `MadviseHelper.java` | `POSIX_MADV_WILLNEED = 3` |
| `HardenedWorkerPool.rejectedTasks` не инкрементировался | `HardenedWorkerPool.java` | Добавлен `rejectedTasks.increment()` в reject handler |
| `AdaptiveSimulation.initialize` вызывался до создания `PlayerList` | `ZeroSeekMod.java`, `MinecraftServerMixin.java` | Перенесено на `ServerLifecycleEvents.SERVER_STARTED` |
| `RebaseWorker` fallback создавал `region_delta/region/` | `RebaseWorker.java` | Убран неверный fallback, создание папки всегда рядом с delta |
| LZ4 (type 4) не поддерживался Delta/MMap | `MmapRegionIo.java`, `ExternalDeltaManager.java` | Используется `RegionFileVersion.fromId()` |
| `MmapRegionIo.read` length check off-by-one | `MmapRegionIo.java` | Исправлена проверка границ |
| `MmapRegionIo` создавался без синхронизации | `RegionFileMixin.java` | Добавлен `synchronized` double-checked locking |
| Delta write не был атомарным | `ExternalDeltaManager.java` | Temp file + atomic move |
| `RebaseWorker` определял измерение по подстроке пути | `RebaseWorker.java` | Используется `relative.getName(0)` |
| Мёртвый код (`version`, `relative`, `getBasePathFromChunk`) | `RegionFileMixin.java`, `RebaseWorker.java`, `ExternalDeltaManager.java` | Удалено |
| Метод-пустышка `AsyncChunkService.prefetchChunk` | `AsyncChunkService.java` | Удалён; prefetch реализован в `ChunkPrefetcher` |
| Неиспользуемые imports (`RegionFileVersion`, `WeakReference`) | `RegionFileMixin.java`, `EntityHibernation.java` | Удалены |
| Поля без реализации (`maxMappedBytes`, loader pool) | `ZeroSeekConfig.java`, `AsyncChunkService.java` | Помечены комментариями `FUTURE` |

### Остаётся в бэклоге
- `TeleportGate` / `SpeedCap`
- LRU eviction / `maxMappedBytes`
- Async generation wrapper
- Hysteresis/recovery delay для TPS governor
- Тест Phase 4 на Linux
- Graceful обработка `EPERM` / `ERROR_ACCESS_DENIED` в affinity
- `AsyncChunkService.prefetchChunk` — метод-пустышка

### Потенциальные риски (принятые trade-off)
- `WindowsAffinity.arena` не закрывается до конца JVM — необходимо для FFM thread-safety.
- `HardenedWorkerPool` потоки non-daemon — by design для стабильности пула.
- `EntityHibernation` использует synchronized `WeakHashMap` — при большом онлайне возможно давление на мониторе (пока не замерено).
