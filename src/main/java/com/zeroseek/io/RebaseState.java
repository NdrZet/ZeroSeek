package com.zeroseek.io;

/**
 * Thread-safe flag indicating whether a rebase operation is in progress.
 * Lives outside of mixins to avoid Mixin visibility restrictions on static members.
 */
public class RebaseState {
    private static volatile boolean rebasing = false;

    public static boolean isRebasing() {
        return rebasing;
    }

    public static void setRebasing(boolean value) {
        rebasing = value;
    }
}
