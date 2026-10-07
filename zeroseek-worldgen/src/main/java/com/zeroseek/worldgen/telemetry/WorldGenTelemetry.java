package com.zeroseek.worldgen.telemetry;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.Map;

/**
 * High-performance lock-free telemetry aggregator for world generation chunk step execution.
 */
public final class WorldGenTelemetry {

    public static final class StepMetric {
        public final LongAdder totalDurationNs = new LongAdder();
        public final LongAdder totalCount = new LongAdder();

        public void record(long durationNs) {
            totalDurationNs.add(durationNs);
            totalCount.increment();
        }

        public double getAverageMillis() {
            long count = totalCount.sum();
            if (count == 0) return 0.0;
            return (totalDurationNs.sum() / 1_000_000.0) / count;
        }

        public long getCount() {
            return totalCount.sum();
        }
    }

    private static final ConcurrentHashMap<String, StepMetric> METRICS = new ConcurrentHashMap<>();

    public static void record(String stepName, long durationNs) {
        if (stepName == null) return;
        METRICS.computeIfAbsent(stepName, k -> new StepMetric()).record(durationNs);
    }

    public static Map<String, StepMetric> getMetrics() {
        return METRICS;
    }

    public static String formatSummary() {
        if (METRICS.isEmpty()) {
            return "§e[ZeroSeek WorldGen]§r No chunk step generation data recorded yet.";
        }

        StringBuilder sb = new StringBuilder("§6[ZeroSeek WorldGen Telemetry]§r Chunk Step Execution:\n");
        METRICS.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().getAverageMillis(), a.getValue().getAverageMillis()))
                .forEach(e -> {
                    StepMetric m = e.getValue();
                    sb.append(String.format("  §b%-18s§r avg: §e%6.2f ms§r (count: %d)\n",
                            e.getKey(), m.getAverageMillis(), m.getCount()));
                });
        return sb.toString();
    }
}
