package com.zeroseek.async.affinity;

/**
 * Platform-specific thread-to-CPU-core binding.
 */
public interface AffinityProvider {

    /**
     * Bind the current thread to the specified CPU cores.
     *
     * @param cores list of logical core IDs
     * @return true if binding succeeded
     */
    boolean bindCurrentThread(int... cores);

    /**
     * @return true if this provider can be used on the current platform
     */
    boolean supported();
}
