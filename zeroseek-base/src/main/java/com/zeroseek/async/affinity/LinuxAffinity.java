package com.zeroseek.async.affinity;

import com.zeroseek.ZeroSeekMod;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Linux affinity provider using FFM API:
 * - gettid()
 * - sched_setaffinity(pid_t, size_t, cpu_set_t*)
 */
public class LinuxAffinity implements AffinityProvider {
    private static final int CPU_SET_BYTES = 128; // supports up to 1024 CPUs
    private final MethodHandle gettid;
    private final MethodHandle schedSetaffinity;
    private final boolean supported;

    public LinuxAffinity() {
        MethodHandle gt = null;
        MethodHandle sa = null;
        boolean ok = false;
        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup lookup = linker.defaultLookup();

            gt = linker.downcallHandle(
                    lookup.find("gettid").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT)
            );
            sa = linker.downcallHandle(
                    lookup.find("sched_setaffinity").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT,  // pid_t
                            ValueLayout.JAVA_LONG, // size_t (x86_64)
                            ValueLayout.ADDRESS    // cpu_set_t*
                    )
            );
            ok = true;
        } catch (Throwable t) {
            ZeroSeekMod.LOGGER.warn("Linux affinity native symbols not available", t);
        }
        this.gettid = gt;
        this.schedSetaffinity = sa;
        this.supported = ok;
    }

    @Override
    public boolean bindCurrentThread(int... cores) {
        if (!supported || cores == null || cores.length == 0) {
            return false;
        }
        try {
            int tid = (int) gettid.invokeExact();
            byte[] cpuset = new byte[CPU_SET_BYTES];
            for (int core : cores) {
                if (core >= 0 && core < CPU_SET_BYTES * 8) {
                    cpuset[core / 8] |= (byte) (1 << (core % 8));
                }
            }

            try (Arena arena = Arena.ofConfined()) {
                MemorySegment seg = arena.allocateFrom(ValueLayout.JAVA_BYTE, cpuset);
                int ret = (int) schedSetaffinity.invokeExact(tid, (long) cpuset.length, seg);
                if (ret != 0) {
                    ZeroSeekMod.LOGGER.warn("sched_setaffinity failed for tid {} cores {} ret={}", tid, cores, ret);
                    return false;
                }
                if (ZeroSeekMod.CONFIG.debugMmap) {
                    ZeroSeekMod.LOGGER.debug("Bound thread {} to cores {}", tid, cores);
                }
                return true;
            }
        } catch (Throwable t) {
            ZeroSeekMod.LOGGER.warn("Failed to bind Linux thread affinity", t);
            return false;
        }
    }

    @Override
    public boolean supported() {
        return supported;
    }
}
