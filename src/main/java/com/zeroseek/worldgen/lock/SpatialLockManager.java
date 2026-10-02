package com.zeroseek.worldgen.lock;

import com.zeroseek.ZeroSeekMod;
import com.zeroseek.tps.TPSState;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * High-performance, non-blocking spatial lock manager for Minecraft world generation.
 * <p>
 * Eliminates deadlocks via canonical lock ordering (coordinate sorting) and provides
 * non-blocking asynchronous queueing using {@link CompletableFuture} chaining.
 * <p>
 * Integrated with Safe TPS Governor: under CRITICAL TPS pressure, heavy boundary tasks
 * (radius > 0, e.g. FEATURES) are throttled to at most {@value #MAX_CRITICAL_CONCURRENT_BOUNDARY_TASKS}
 * concurrent operations using an asynchronous permit queue. Crucially, permits are acquired
 * BEFORE spatial locks are reserved, completely eliminating permit-coordinate deadlocks.
 */
public class SpatialLockManager {

    /**
     * Maximum concurrent boundary tasks (radius > 0) allowed during CRITICAL TPS state.
     */
    public static final int MAX_CRITICAL_CONCURRENT_BOUNDARY_TASKS = 2;

    private final ConcurrentHashMap<Long, CompletableFuture<Void>> lockMap = new ConcurrentHashMap<>();
    private final Executor executor;

    // TPS Governor feedback queue & counters
    private final Object permitLock = new Object();
    private final AtomicInteger activeBoundaryTasks = new AtomicInteger(0);
    private final ConcurrentLinkedQueue<CompletableFuture<Void>> boundaryPermitQueue = new ConcurrentLinkedQueue<>();

    /**
     * Creates a {@link SpatialLockManager} using {@link ForkJoinPool#commonPool()} for async chaining.
     */
    public SpatialLockManager() {
        this(ForkJoinPool.commonPool());
    }

    /**
     * Creates a {@link SpatialLockManager} using the specified {@link Executor}.
     *
     * @param executor Executor used to dispatch queued tasks when dependencies complete.
     */
    public SpatialLockManager(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * Checks whether the TPS Governor is active and currently in the CRITICAL state.
     */
    public static boolean isGovernorCritical() {
        return ZeroSeekMod.TPS_MONITOR != null
                && ZeroSeekMod.CONFIG != null
                && ZeroSeekMod.CONFIG.tpsGovernorEnabled
                && ZeroSeekMod.TPS_MONITOR.getState() == TPSState.CRITICAL;
    }

    /**
     * Asynchronously acquires an execution permit for boundary tasks (radius > 0).
     * <p>
     * Non-blocking guarantee: Under NORMAL or STRESS TPS, immediately completes. Under CRITICAL TPS,
     * throttles concurrency to {@link #MAX_CRITICAL_CONCURRENT_BOUNDARY_TASKS}.
     * <p>
     * Crucially executed BEFORE acquiring any spatial locks in {@link #lockMap}.
     */
    private CompletableFuture<Void> acquireBoundaryPermit() {
        synchronized (permitLock) {
            if (!isGovernorCritical()) {
                drainPermitQueueUnderLock();
                activeBoundaryTasks.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            }

            if (activeBoundaryTasks.get() < MAX_CRITICAL_CONCURRENT_BOUNDARY_TASKS) {
                activeBoundaryTasks.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            }

            CompletableFuture<Void> waiter = new CompletableFuture<>();
            boundaryPermitQueue.add(waiter);
            return waiter;
        }
    }

    /**
     * Releases a boundary task permit and hands off to queued waiters or decrements active task count.
     */
    private void releaseBoundaryPermit() {
        synchronized (permitLock) {
            if (!isGovernorCritical()) {
                drainPermitQueueUnderLock();
                activeBoundaryTasks.updateAndGet(c -> Math.max(0, c - 1));
                return;
            }

            while (!boundaryPermitQueue.isEmpty()) {
                CompletableFuture<Void> next = boundaryPermitQueue.poll();
                if (next != null && !next.isDone()) {
                    // Transfer permit to next waiting task without decrementing active counter
                    next.complete(null);
                    return;
                }
            }

            activeBoundaryTasks.updateAndGet(c -> Math.max(0, c - 1));
        }
    }

    /**
     * Resets governor permits and unblocks all queued boundary tasks when server returns
     * to NORMAL or STRESS TPS conditions.
     */
    public void resetGovernorPermits() {
        synchronized (permitLock) {
            drainPermitQueueUnderLock();
        }
    }

    private void drainPermitQueueUnderLock() {
        while (!boundaryPermitQueue.isEmpty()) {
            CompletableFuture<Void> waiter = boundaryPermitQueue.poll();
            if (waiter != null && !waiter.isDone()) {
                activeBoundaryTasks.incrementAndGet();
                waiter.complete(null);
            }
        }
    }

    /**
     * Executes the supplied asynchronous action while holding exclusive spatial locks on all chunks
     * within the specified Chebyshev radius {@code [-radius..radius]} around {@code center}.
     * <p>
     * Fast-path: When {@code radius <= 0}, locking and governor throttling are completely bypassed.
     * When {@code radius > 0}, non-blocking permit acquisition precedes spatial lock acquisition.
     *
     * @param center Chunk center position.
     * @param radius Chunk radius around center (Chebyshev distance).
     * @param action Asynchronous action to execute once locks are acquired.
     * @param <T>    Result type of the action.
     * @return A {@link CompletableFuture} representing the completion of the action.
     */
    public <T> CompletableFuture<T> computeWithLock(ChunkPos center, int radius, Supplier<CompletableFuture<T>> action) {
        Objects.requireNonNull(center, "center must not be null");
        Objects.requireNonNull(action, "action must not be null");

        // Fast-path bypass for radius <= 0
        if (radius <= 0) {
            return action.get();
        }

        CompletableFuture<T> resultFuture = new CompletableFuture<>();
        CompletableFuture<Void> permitFuture = acquireBoundaryPermit();

        if (permitFuture.isDone()) {
            acquireSpatialLocksAndExecute(center, radius, action, resultFuture);
        } else {
            permitFuture.whenCompleteAsync((v, err) -> {
                if (resultFuture.isCancelled()) {
                    releaseBoundaryPermit();
                    return;
                }
                acquireSpatialLocksAndExecute(center, radius, action, resultFuture);
            }, executor);
        }

        return resultFuture;
    }

    private <T> void acquireSpatialLocksAndExecute(
            ChunkPos center,
            int radius,
            Supplier<CompletableFuture<T>> action,
            CompletableFuture<T> resultFuture
    ) {
        if (resultFuture.isCancelled()) {
            releaseBoundaryPermit();
            return;
        }

        // Canonical Lock Ordering:
        // Collect coordinates of all chunks in [-radius..radius] for X and Z.
        int side = 2 * radius + 1;
        long[] positions = new long[side * side];
        int idx = 0;
        int centerX = center.x;
        int centerZ = center.z;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                positions[idx++] = ChunkPos.asLong(centerX + dx, centerZ + dz);
            }
        }

        // Sort coordinates in ascending order (canonical ordering by Long.compare).
        // This guarantees a strict total ordering of chunk acquisitions across all concurrent tasks,
        // mathematically eliminating cyclic wait-for deadlocks.
        Arrays.sort(positions);

        CompletableFuture<Void> myLock = new CompletableFuture<>();
        List<CompletableFuture<Void>> dependencies = new ArrayList<>();
        Set<CompletableFuture<Void>> seen = new HashSet<>();

        synchronized (this) {
            for (long pos : positions) {
                CompletableFuture<Void> prev = lockMap.put(pos, myLock);
                if (prev != null && !prev.isDone() && seen.add(prev)) {
                    dependencies.add(prev);
                }
            }
        }

        if (dependencies.isEmpty()) {
            // Immediate execution path: no overlapping tasks are currently holding locks
            executeAction(action, positions, myLock, resultFuture);
        } else {
            // Chained execution path: wait for all predecessor locks to complete
            CompletableFuture<Void> allDeps = CompletableFuture.allOf(dependencies.toArray(new CompletableFuture[0]));
            allDeps.whenCompleteAsync((v, err) -> {
                executeAction(action, positions, myLock, resultFuture);
            }, executor);
        }
    }

    private <T> void executeAction(
            Supplier<CompletableFuture<T>> action,
            long[] positions,
            CompletableFuture<Void> myLock,
            CompletableFuture<T> resultFuture
    ) {
        if (resultFuture.isCancelled()) {
            try {
                releaseLocks(positions, myLock);
            } finally {
                releaseBoundaryPermit();
            }
            return;
        }

        CompletableFuture<T> actionFuture;
        try {
            actionFuture = action.get();
            if (actionFuture == null) {
                actionFuture = CompletableFuture.completedFuture(null);
            }
        } catch (Throwable t) {
            actionFuture = CompletableFuture.failedFuture(t);
        }

        actionFuture.whenComplete((res, ex) -> {
            try {
                releaseLocks(positions, myLock);
            } finally {
                try {
                    releaseBoundaryPermit();
                } finally {
                    if (ex != null) {
                        resultFuture.completeExceptionally(ex);
                    } else {
                        resultFuture.complete(res);
                    }
                }
            }
        });
    }

    private void releaseLocks(long[] positions, CompletableFuture<Void> myLock) {
        synchronized (this) {
            for (long pos : positions) {
                lockMap.compute(pos, (k, current) -> current == myLock ? null : current);
            }
        }
        myLock.complete(null);
    }

    /**
     * Checks if a chunk at the specified position is currently locked or has queued operations.
     */
    public boolean isLocked(ChunkPos pos) {
        if (pos == null) {
            return false;
        }
        return isLocked(pos.toLong());
    }

    /**
     * Checks if a packed chunk position is currently locked or has queued operations.
     */
    public boolean isLocked(long chunkPos) {
        CompletableFuture<Void> lock = lockMap.get(chunkPos);
        return lock != null && !lock.isDone();
    }

    /**
     * Returns the total number of currently active chunk locks in the manager.
     */
    public int getActiveLocksCount() {
        int count = 0;
        for (CompletableFuture<Void> lock : lockMap.values()) {
            if (lock != null && !lock.isDone()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the number of currently active boundary tasks.
     */
    public int getActiveBoundaryTasks() {
        return activeBoundaryTasks.get();
    }

    /**
     * Returns the number of boundary tasks waiting in the permit queue.
     */
    public int getQueuedBoundaryTasksCount() {
        int count = 0;
        for (CompletableFuture<Void> f : boundaryPermitQueue) {
            if (f != null && !f.isDone()) {
                count++;
            }
        }
        return count;
    }
}
