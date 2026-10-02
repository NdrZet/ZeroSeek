package com.zeroseek.worldgen.lock;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class SpatialLockManagerTest {

    @BeforeAll
    public static void setUp() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void testFastPathZeroRadius() throws Exception {
        SpatialLockManager manager = new SpatialLockManager();
        ChunkPos center = new ChunkPos(5, 5);

        // Fast-path: radius == 0
        CompletableFuture<String> f0 = manager.computeWithLock(center, 0, () -> CompletableFuture.completedFuture("fast-0"));
        assertTrue(f0.isDone());
        assertEquals("fast-0", f0.get());
        assertFalse(manager.isLocked(center));
        assertEquals(0, manager.getActiveLocksCount());

        // Fast-path: negative radius
        CompletableFuture<String> fNeg = manager.computeWithLock(center, -1, () -> CompletableFuture.completedFuture("fast-neg"));
        assertTrue(fNeg.isDone());
        assertEquals("fast-neg", fNeg.get());
        assertFalse(manager.isLocked(center));
        assertEquals(0, manager.getActiveLocksCount());

        // Fast-path with async future: locks must not be acquired
        CompletableFuture<String> delayed = new CompletableFuture<>();
        CompletableFuture<String> fAsync = manager.computeWithLock(center, 0, () -> delayed);
        assertFalse(fAsync.isDone());
        assertFalse(manager.isLocked(center), "Fast-path must not lock any chunks");
        assertEquals(0, manager.getActiveLocksCount());

        delayed.complete("async-ok");
        assertEquals("async-ok", fAsync.get());
        assertEquals(0, manager.getActiveLocksCount());
    }

    @Test
    public void testNonOverlappingChunksRunConcurrently() throws Exception {
        SpatialLockManager manager = new SpatialLockManager();
        int radius = 1;
        ChunkPos pos1 = new ChunkPos(0, 0);
        ChunkPos pos2 = new ChunkPos(10, 10); // dist = 10 > 2 * radius (2)

        CountDownLatch task1Started = new CountDownLatch(1);
        CountDownLatch task2Started = new CountDownLatch(1);
        CountDownLatch releaseBoth = new CountDownLatch(1);

        CompletableFuture<String> f1 = manager.computeWithLock(pos1, radius, () -> CompletableFuture.supplyAsync(() -> {
            task1Started.countDown();
            try {
                boolean unblocked = releaseBoth.await(5, TimeUnit.SECONDS);
                if (!unblocked) throw new RuntimeException("Timed out waiting for release");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "task1";
        }));

        CompletableFuture<String> f2 = manager.computeWithLock(pos2, radius, () -> CompletableFuture.supplyAsync(() -> {
            task2Started.countDown();
            try {
                boolean unblocked = releaseBoth.await(5, TimeUnit.SECONDS);
                if (!unblocked) throw new RuntimeException("Timed out waiting for release");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "task2";
        }));

        // Both tasks should start concurrently and reach the barrier while holding locks
        assertTrue(task1Started.await(5, TimeUnit.SECONDS), "Task 1 should have started concurrently");
        assertTrue(task2Started.await(5, TimeUnit.SECONDS), "Task 2 should have started concurrently");

        // Both regions should be concurrently locked: 9 chunks each -> 18 total
        assertTrue(manager.isLocked(pos1));
        assertTrue(manager.isLocked(pos2));
        assertEquals(18, manager.getActiveLocksCount());

        // Release both tasks
        releaseBoth.countDown();

        assertEquals("task1", f1.get(5, TimeUnit.SECONDS));
        assertEquals("task2", f2.get(5, TimeUnit.SECONDS));

        // After completion, all locks must be released
        assertEquals(0, manager.getActiveLocksCount());
        assertFalse(manager.isLocked(pos1));
        assertFalse(manager.isLocked(pos2));
    }

    @Test
    public void testOverlappingChunksSerialized() throws Exception {
        SpatialLockManager manager = new SpatialLockManager();
        int radius = 1;
        ChunkPos pos1 = new ChunkPos(0, 0);
        ChunkPos pos2 = new ChunkPos(1, 0); // overlaps with pos1 (shared chunks at X in [0, 1])

        AtomicBoolean task1Running = new AtomicBoolean(false);
        AtomicBoolean task2StartedWhileTask1Running = new AtomicBoolean(false);
        AtomicBoolean task1Completed = new AtomicBoolean(false);

        CompletableFuture<Void> task1Gate = new CompletableFuture<>();

        CompletableFuture<String> f1 = manager.computeWithLock(pos1, radius, () -> {
            task1Running.set(true);
            return task1Gate.thenApply(v -> {
                task1Running.set(false);
                task1Completed.set(true);
                return "res1";
            });
        });

        CompletableFuture<String> f2 = manager.computeWithLock(pos2, radius, () -> {
            if (task1Running.get() || !task1Completed.get()) {
                task2StartedWhileTask1Running.set(true);
            }
            return CompletableFuture.completedFuture("res2");
        });

        // Verify task 1 is running and holding locks
        assertTrue(manager.isLocked(pos1));
        assertFalse(f2.isDone(), "Task 2 must not complete while Task 1 is still holding locks");
        assertFalse(task1Completed.get(), "Task 1 must still be running");

        // Complete task 1 to allow task 2 to proceed
        task1Gate.complete(null);

        assertEquals("res1", f1.get(5, TimeUnit.SECONDS));
        assertEquals("res2", f2.get(5, TimeUnit.SECONDS));

        assertFalse(task2StartedWhileTask1Running.get(), "Task 2 must not run concurrently with Task 1!");
        assertTrue(task1Completed.get(), "Task 1 must have completed");
        assertEquals(0, manager.getActiveLocksCount(), "All locks must be released");
    }

    @Test
    public void testDeadlockFreedomStressTest() throws Exception {
        SpatialLockManager manager = new SpatialLockManager();
        int threadCount = 16;
        int taskCount = 300;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            AtomicInteger completedCount = new AtomicInteger(0);
            List<CompletableFuture<Integer>> futures = new ArrayList<>(taskCount);
            Random random = new Random(42);

            CountDownLatch startGate = new CountDownLatch(1);
            for (int i = 0; i < taskCount; i++) {
                final int taskId = i;
                int cx = random.nextInt(4);
                int cz = random.nextInt(4);
                int radius = 1; // 3x3 region on a small 4x4 coordinate space -> heavy overlapping

                CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        startGate.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return manager.computeWithLock(new ChunkPos(cx, cz), radius, () -> {
                        completedCount.incrementAndGet();
                        return CompletableFuture.completedFuture(taskId);
                    });
                }, pool).thenCompose(f -> f);

                futures.add(future);
            }

            // Release all threads simultaneously
            startGate.countDown();

            // Wait for all tasks with timeout (deadlock detection)
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(15, TimeUnit.SECONDS);

            assertEquals(taskCount, completedCount.get(), "All tasks must complete without deadlock");
            assertEquals(0, manager.getActiveLocksCount(), "All locks must be released after completion");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void testSpatialLockTokenRecordAndHelper() {
        SpatialLockToken writeToken = SpatialLockToken.of(12, -34, SpatialLockToken.LockUsage.WORLDGEN_WRITE);
        assertEquals(ChunkPos.asLong(12, -34), writeToken.chunkPos());
        assertEquals(SpatialLockToken.LockUsage.WORLDGEN_WRITE, writeToken.usage());
        assertEquals(12, writeToken.chunkX());
        assertEquals(-34, writeToken.chunkZ());
        assertEquals(new ChunkPos(12, -34), writeToken.toChunkPos());

        SpatialLockToken readToken = SpatialLockToken.of(new ChunkPos(7, 8), SpatialLockToken.LockUsage.WORLDGEN_READ);
        assertEquals(ChunkPos.asLong(7, 8), readToken.chunkPos());
        assertEquals(SpatialLockToken.LockUsage.WORLDGEN_READ, readToken.usage());
        assertEquals(7, readToken.chunkX());
        assertEquals(8, readToken.chunkZ());
    }

    @Test
    public void testExceptionInActionReleasesLocks() throws Exception {
        SpatialLockManager manager = new SpatialLockManager();
        ChunkPos pos = new ChunkPos(0, 0);

        CompletableFuture<String> failing = manager.computeWithLock(pos, 1, () -> {
            return CompletableFuture.failedFuture(new IllegalStateException("Simulated worldgen failure"));
        });

        CompletableFuture<String> successor = manager.computeWithLock(pos, 1, () -> {
            return CompletableFuture.completedFuture("recovered");
        });

        // The first task should complete exceptionally
        ExecutionException ex = assertThrows(ExecutionException.class, () -> failing.get(5, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, ex.getCause());

        // The successor MUST still execute and succeed despite predecessor failure
        assertEquals("recovered", successor.get(5, TimeUnit.SECONDS));
        assertEquals(0, manager.getActiveLocksCount());
        assertFalse(manager.isLocked(pos));
    }

    @Test
    public void testCriticalGovernorThrottlesBoundaryTasksToTwo() throws Exception {
        com.zeroseek.ZeroSeekMod.CONFIG = new com.zeroseek.config.ZeroSeekConfig();
        com.zeroseek.ZeroSeekMod.CONFIG.tpsGovernorEnabled = true;
        com.zeroseek.ZeroSeekMod.TPS_MONITOR = new com.zeroseek.tps.TPSMonitor();
        com.zeroseek.ZeroSeekMod.TPS_MONITOR.setState(com.zeroseek.tps.TPSState.CRITICAL);

        SpatialLockManager manager = new SpatialLockManager();
        com.zeroseek.ZeroSeekMod.SPATIAL_LOCK_MANAGER = manager;

        CompletableFuture<Void> gate1 = new CompletableFuture<>();
        CompletableFuture<Void> gate2 = new CompletableFuture<>();

        CompletableFuture<String> task1 = manager.computeWithLock(new ChunkPos(0, 0), 1, () -> gate1.thenApply(v -> "t1"));
        CompletableFuture<String> task2 = manager.computeWithLock(new ChunkPos(10, 10), 1, () -> gate2.thenApply(v -> "t2"));

        assertEquals(2, manager.getActiveBoundaryTasks());
        assertEquals(0, manager.getQueuedBoundaryTasksCount());

        // 3rd boundary task must be throttled into permit queue without acquiring locks
        CompletableFuture<String> task3 = manager.computeWithLock(new ChunkPos(20, 20), 1, () -> CompletableFuture.completedFuture("t3"));

        assertEquals(2, manager.getActiveBoundaryTasks());
        assertEquals(1, manager.getQueuedBoundaryTasksCount());
        assertFalse(task3.isDone(), "Task 3 must wait in permit queue during CRITICAL TPS");
        assertFalse(manager.isLocked(new ChunkPos(20, 20)), "Task 3 must NOT hold spatial locks while waiting for permit");

        // Release task 1 -> permit transfers to task 3
        gate1.complete(null);
        assertEquals("t1", task1.get(2, TimeUnit.SECONDS));
        assertEquals("t3", task3.get(2, TimeUnit.SECONDS));

        // Release task 2
        gate2.complete(null);
        assertEquals("t2", task2.get(2, TimeUnit.SECONDS));

        assertEquals(0, manager.getActiveBoundaryTasks());
        assertEquals(0, manager.getQueuedBoundaryTasksCount());
        assertEquals(0, manager.getActiveLocksCount());

        com.zeroseek.ZeroSeekMod.TPS_MONITOR.setState(com.zeroseek.tps.TPSState.NORMAL);
    }

    @Test
    public void testPermitsResetWhenTransitioningToNormal() throws Exception {
        com.zeroseek.ZeroSeekMod.CONFIG = new com.zeroseek.config.ZeroSeekConfig();
        com.zeroseek.ZeroSeekMod.CONFIG.tpsGovernorEnabled = true;
        com.zeroseek.ZeroSeekMod.TPS_MONITOR = new com.zeroseek.tps.TPSMonitor();
        com.zeroseek.ZeroSeekMod.TPS_MONITOR.setState(com.zeroseek.tps.TPSState.CRITICAL);

        SpatialLockManager manager = new SpatialLockManager();
        com.zeroseek.ZeroSeekMod.SPATIAL_LOCK_MANAGER = manager;

        CompletableFuture<Void> hold = new CompletableFuture<>();
        CompletableFuture<String> task1 = manager.computeWithLock(new ChunkPos(0, 0), 1, () -> hold.thenApply(v -> "t1"));
        CompletableFuture<String> task2 = manager.computeWithLock(new ChunkPos(10, 10), 1, () -> hold.thenApply(v -> "t2"));
        CompletableFuture<String> task3 = manager.computeWithLock(new ChunkPos(20, 20), 1, () -> CompletableFuture.completedFuture("t3"));

        assertEquals(1, manager.getQueuedBoundaryTasksCount());

        // Transition back to NORMAL -> permits must drain immediately
        com.zeroseek.ZeroSeekMod.TPS_MONITOR.setState(com.zeroseek.tps.TPSState.NORMAL);

        assertEquals("t3", task3.get(2, TimeUnit.SECONDS));
        assertEquals(0, manager.getQueuedBoundaryTasksCount());

        hold.complete(null);
        assertEquals("t1", task1.get(2, TimeUnit.SECONDS));
        assertEquals("t2", task2.get(2, TimeUnit.SECONDS));
    }
}
