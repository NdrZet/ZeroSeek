package com.zeroseek.async;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hardened thread pool with bounded queue and strict discard policy.
 * Designed for ZeroSeek async workers to prevent memory bloat and CPU contention.
 */
public class HardenedWorkerPool {
    private final ThreadPoolExecutor executor;
    private final String name;

    public HardenedWorkerPool(String name, int threads, int maxQueueSize) {
        this.name = name;
        this.executor = new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(maxQueueSize),
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, name + "-" + counter.incrementAndGet());
                        t.setDaemon(false);
                        t.setPriority(Thread.MAX_PRIORITY);
                        return t;
                    }
                },
                new ThreadPoolExecutor.DiscardPolicy()
        );
        this.executor.prestartAllCoreThreads();
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

    public void shutdown() {
        executor.shutdown();
    }

    public String getName() {
        return name;
    }
}
