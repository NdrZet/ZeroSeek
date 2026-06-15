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
 * Windows affinity provider using FFM API:
 * - GetCurrentThread()
 * - SetThreadAffinityMask(HANDLE, DWORD_PTR)
 */
public class WindowsAffinity implements AffinityProvider {
    private final Arena arena;
    private final MethodHandle getCurrentThread;
    private final MethodHandle setThreadAffinityMask;
    private final boolean supported;

    public WindowsAffinity() {
        Arena a = null;
        MethodHandle gct = null;
        MethodHandle stam = null;
        boolean ok = false;
        try {
            a = Arena.ofShared();
            Linker linker = Linker.nativeLinker();
            SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", a);

            gct = linker.downcallHandle(
                    kernel32.find("GetCurrentThread").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS)
            );
            stam = linker.downcallHandle(
                    kernel32.find("SetThreadAffinityMask").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                            ValueLayout.ADDRESS, // HANDLE
                            ValueLayout.JAVA_LONG // DWORD_PTR (x86_64)
                    )
            );
            ok = true;
        } catch (Throwable t) {
            ZeroSeekMod.LOGGER.warn("Windows affinity native symbols not available", t);
            if (a != null) {
                try { a.close(); } catch (Exception ignored) {}
            }
        }
        this.arena = a;
        this.getCurrentThread = gct;
        this.setThreadAffinityMask = stam;
        this.supported = ok;
    }

    @Override
    public boolean bindCurrentThread(int... cores) {
        if (!supported || cores == null || cores.length == 0) {
            return false;
        }
        try {
            long mask = 0;
            for (int core : cores) {
                if (core >= 0 && core < 64) {
                    mask |= (1L << core);
                }
            }
            if (mask == 0) {
                return false;
            }

            MemorySegment handle = (MemorySegment) getCurrentThread.invokeExact();
            long result = (long) setThreadAffinityMask.invokeExact(handle, mask);
            if (result == 0) {
                ZeroSeekMod.LOGGER.warn("SetThreadAffinityMask failed for cores {}", cores);
                return false;
            }
            if (ZeroSeekMod.CONFIG.debugMmap) {
                ZeroSeekMod.LOGGER.debug("Bound Windows thread to cores {} mask={}", cores, mask);
            }
            return true;
        } catch (Throwable t) {
            ZeroSeekMod.LOGGER.warn("Failed to bind Windows thread affinity", t);
            return false;
        }
    }

    @Override
    public boolean supported() {
        return supported;
    }
}
