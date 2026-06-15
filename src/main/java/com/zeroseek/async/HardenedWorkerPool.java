package com.zeroseek.async;

import com.zeroseek.ZeroSeekMod;
import com.zeroseek.async.affinity.PlatformAffinity;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Hardened thread pool with bounded queue and strict discard policy.
 * Designed for ZeroSeek async workers to prevent memory bloat and CPU contention.
 */
public class HardenedWorkerPool {
    private final ThreadPoolExecutor executor;
    private final String name;
    private final int[] affinityCores;
    private final LongAdder rejectedTasks = new LongAdder();

    public HardenedWorkerPool(String name, int threads, int maxQueueSize) {
        this(name, threads, maxQueueSize, null);
    }

    public HardenedWorkerPool(String name, int threads, int maxQueueSize, int[] affinityCores) {
        this.name = name;
        this.affinityCores = affinityCores;
        this.executor = new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(maxQueueSize),
                new AffinityThreadFactory(name, affinityCores),
                createRejectHandler(name)
        );
        this.executor.prestartAllCoreThreads();
    }

    private static RejectedExecutionHandler createRejectHandler(String name) {
        return (r, pool) -> {
            if (!pool.isShutdown()) {
                pool.getQueue().poll();
                try {
                    pool.execute(r);
                } catch (Exception e) {
                    ZeroSeekMod.LOGGER.warn("{} failed to re-submit task after discarding oldest", name, e);
                }
                if (pool.getCompletedTaskCount() % 1000L == 0L) {
                    ZeroSeekMod.LOGGER.warn(
                            "{} queue overflow (size={}). Discarded oldest task.",
                            name, pool.getQueue().size()
                    );
                }
            }
        };
    }

    public Executor getExecutor() {
        return executor;
    }

    public ExecutorService getExecutorService() {
        return executor;
    }

    public ThreadPoolExecutor getRawExecutor() {
        return executor;
    }

    public int getActiveCount() {
        return executor.getActiveCount();
    }

    public int getQueueSize() {
        return executor.getQueue().size();
    }

    public long getCompletedTaskCount() {
        return executor.getCompletedTaskCount();
    }

    public long getRejectedCount() {
        return rejectedTasks.sum();
    }

    public void shutdown() {
        executor.shutdown();
    }

    public String getName() {
        return name;
    }

    public int[] getAffinityCores() {
        return affinityCores;
    }

    /**
     * Thread that binds itself to configured CPU cores on first run.
     */
    private static final class AffinityThread extends Thread {
        private final int[] cores;
        private volatile boolean bound;

        AffinityThread(Runnable r, String name, int[] cores) {
            super(r, name);
            this.cores = cores;
            setDaemon(false);
            setPriority(Thread.MAX_PRIORITY);
        }

        @Override
        public void run() {
            if (!bound && cores != null && cores.length > 0) {
                bound = PlatformAffinity.bindCurrentThread(cores);
                if (ZeroSeekMod.CONFIG != null && ZeroSeekMod.CONFIG.debugMmap) {
                    ZeroSeekMod.LOGGER.debug(
                            "Thread {} affinity bound to {}: {}",
                            getName(), cores, bound
                    );
                }
            }
            super.run();
        }
    }

    private static final class AffinityThreadFactory implements ThreadFactory {
        private final String name;
        private final int[] cores;
        private final AtomicInteger counter = new AtomicInteger(0);

        AffinityThreadFactory(String name, int[] cores) {
            this.name = name;
            this.cores = cores;
        }

        @Override
        public Thread newThread(Runnable r) {
            return new AffinityThread(r, name + "-" + counter.incrementAndGet(), cores);
        }
    }
}
