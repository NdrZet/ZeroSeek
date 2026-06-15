package com.zeroseek.io;

import com.zeroseek.ZeroSeekMod;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * OS-level memory advice for mapped regions.
 * Linux: posix_madvise.
 * Windows: not implemented (PrefetchVirtualMemory can be added later).
 */
public final class MadviseHelper {
    private static final int POSIX_MADV_WILLNEED = 1;
    private static final int POSIX_MADV_DONTNEED = 4;

    private static final MethodHandle POSIX_MADVISE;
    private static final boolean SUPPORTED;

    static {
        MethodHandle handle = null;
        boolean ok = false;
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("linux")) {
            try {
                Linker linker = Linker.nativeLinker();
                SymbolLookup lookup = linker.defaultLookup();
                handle = linker.downcallHandle(
                        lookup.find("posix_madvise").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_LONG,
                                ValueLayout.JAVA_INT
                        )
                );
                ok = true;
            } catch (Throwable t) {
                ZeroSeekMod.LOGGER.warn("posix_madvise not available", t);
            }
        }
        POSIX_MADVISE = handle;
        SUPPORTED = ok;
    }

    private MadviseHelper() {
    }

    public static boolean willNeed(MemorySegment segment) {
        return advise(segment, POSIX_MADV_WILLNEED);
    }

    public static boolean dontNeed(MemorySegment segment) {
        return advise(segment, POSIX_MADV_DONTNEED);
    }

    private static boolean advise(MemorySegment segment, int advice) {
        if (!SUPPORTED || segment == null) {
            return false;
        }
        try {
            int ret = (int) POSIX_MADVISE.invokeExact(segment, segment.byteSize(), advice);
            return ret == 0;
        } catch (Throwable t) {
            ZeroSeekMod.LOGGER.warn("posix_madvise failed", t);
            return false;
        }
    }

    public static boolean supported() {
        return SUPPORTED;
    }
}
