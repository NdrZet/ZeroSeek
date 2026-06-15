package com.zeroseek.async.affinity;

import com.zeroseek.ZeroSeekMod;

/**
 * Selects the appropriate affinity provider for the current OS.
 */
public final class PlatformAffinity {
    private static final AffinityProvider PROVIDER;

    static {
        String os = System.getProperty("os.name", "").toLowerCase();
        AffinityProvider candidate;
        if (os.contains("linux")) {
            candidate = new LinuxAffinity();
        } else if (os.contains("windows")) {
            candidate = new WindowsAffinity();
        } else {
            candidate = new NoopAffinity();
        }
        if (!candidate.supported()) {
            candidate = new NoopAffinity();
        }
        PROVIDER = candidate;
    }

    private PlatformAffinity() {
    }

    public static boolean bindCurrentThread(int... cores) {
        return PROVIDER.bindCurrentThread(cores);
    }

    public static boolean supported() {
        return PROVIDER.supported();
    }

    public static String providerName() {
        return PROVIDER.getClass().getSimpleName();
    }

    public static void logStatus() {
        if (supported()) {
            ZeroSeekMod.LOGGER.info("CPU affinity enabled via {}", providerName());
        } else {
            ZeroSeekMod.LOGGER.warn("CPU affinity unavailable on this platform/JVM");
        }
    }
}
