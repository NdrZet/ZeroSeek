# ZeroSeek

Высокопроизводительный MMap + Async движок загрузки чанков для выделенных серверов Minecraft.

## Что такое ZeroSeek?

ZeroSeek заменяет стандартную синхронную систему загрузки чанков Minecraft на движок с отображением памяти (memory-mapped I/O) и асинхронной обработкой. Цель — стабильный TPS при любом онлайне и любом перемещении игроков, включая элитры и телепорты.

## Текущий статус

Готово к использованию: MMap-чтение чанков, Delta Layer, Async Workers, CPU Affinity, TPS Governor.


## Возможности

- **MMap I/O Engine** — чанки читаются напрямую из RAM через `MemorySegment` (Java 22 FFM API).
- **Delta Layer** — запись изолирована от чтения; изменённые чанки хранятся в `.raw` файлах; фоновый rebase в base `.mca`.
- **Async Worker Pools** — парсинг чанков (распаковка + NBT + DataFixerUpper) уходит в dedicated fixed thread pools с bounded queues.
- **CPU Affinity** — жёсткая привязка рабочих потоков к конкретным ядрам через FFM (`sched_setaffinity` / `SetThreadAffinityMask`).
- **TPS Governor** — адаптивное снижение simulation distance, заморозка player tickets, hibernation сущностей, throttling AI и пакетов движения при просадке TPS.
- **Entity Hibernation** — сущности в «старых» чанках пропускают тики при STRESS/CRITICAL TPS; блоки продолжают тикать.

## В плане / не реализовано

- `TeleportGate` — ленивые телепорты, ожидающие готовности чанков.
- `SpeedCap` — ограничение скорости сущностей при CRITICAL TPS.
- LRU eviction / контроль `maxMappedBytes`.
- Windows `PrefetchVirtualMemory` fallback для madvise.
- Async generation wrapper.
- Auto-detect C2ME и автоотключение mmap.

## Целевая платформа

- Minecraft **1.21.11**
- Fabric Loader **0.18.2+**
- Java **22** (для FFM / MemorySegment)
- Только серверная сторона

## Сборка

```bash
./gradlew build
```

Результат: `build/libs/zeroseek.jar`

## Установка

1. Скопируйте `zeroseek.jar` в папку `mods/` вашего сервера.
2. При первом запуске создастся `config/zeroseek.json` — отредактируйте при необходимости.
3. Запустите сервер.

## Конфигурация

`config/zeroseek.json` позволяет настроить:

- `mmapEnabled` / `deltaLayerEnabled` — включение MMap и Delta Layer.
- `maxMappedBytes` — бюджет памяти MMap (LRU eviction пока не реализован).
- `rebaseIntervalSeconds` — интервал фонового rebase.
- `chunkParserThreads` / `chunkLoaderThreads` — размеры пулов.
- `cpuAffinityEnabled` / `cpuAffinityCores` — привязка к ядрам.
- `tpsGovernorEnabled`, `simDistNormal/Stress/Critical`, `tpsStress/CriticalThreshold` — TPS governor.
- `entityHibernationEnabled`, `hibernateMinAgeMs`, `hibernateStressAgeMs` — гибернация сущностей.

## Совместимость

- **Проверено на Windows:** affinity, mmap, delta, rebase, TPS governor работают.
- **Linux:** affinity + madvise реализованы, но не тестировались.
- **Потенциально совместимы:** Terralith, Biomes O' Plenty, Lithium, Starlight, Voxy, Xaero's World Map, Pl3xMap.
- **Конфликт:** C2ME — пока нет auto-detect, при использовании с C2ME рекомендуется отключить `mmapEnabled`.

