package com.zeroseek.async.affinity;

/**
 * No-op affinity provider used when native binding is unavailable.
 */
public class NoopAffinity implements AffinityProvider {

    @Override
    public boolean bindCurrentThread(int... cores) {
        return false;
    }

    @Override
    public boolean supported() {
        return false;
    }
}
