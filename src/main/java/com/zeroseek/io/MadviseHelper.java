package com.zeroseek.io;

import com.zeroseek.ZeroSeekMod;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * OS-level memory advice for mapped regions.
 * Linux: posix_madvise (WILLNEED / DONTNEED).
 * Windows: PrefetchVirtualMemory (WILLNEED only; DONTNEED is a no-op).
 */
public final class MadviseHelper {
    private static final int POSIX_MADV_WILLNEED = 3;
    private static final int POSIX_MADV_DONTNEED = 4;

    private static final boolean IS_LINUX;
    private static final boolean IS_WINDOWS;
    private static final boolean SUPPORTED;

    // Linux
    private static final MethodHandle POSIX_MADVISE;

    // Windows
    private static final Arena WIN_ARENA;
    private static final MethodHandle GET_CURRENT_PROCESS;
    private static final MethodHandle PREFETCH_VIRTUAL_MEMORY;
    private static final MemoryLayout WIN32_MEMORY_RANGE_ENTRY = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("VirtualAddress"),
            ValueLayout.JAVA_LONG.withName("NumberOfBytes")
    );

    static {
        String os = System.getProperty("os.name", "").toLowerCase();
        IS_LINUX = os.contains("linux");
        IS_WINDOWS = os.contains("windows");

        MethodHandle posixMadvise = null;
        MethodHandle getCurrentProcess = null;
        MethodHandle prefetchVirtualMemory = null;
        Arena winArena = null;
        boolean ok = false;

        if (IS_LINUX) {
            try {
                Linker linker = Linker.nativeLinker();
                SymbolLookup lookup = linker.defaultLookup();
                posixMadvise = linker.downcallHandle(
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
        } else if (IS_WINDOWS) {
            try {
                winArena = Arena.ofShared();
                Linker linker = Linker.nativeLinker();
                SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", winArena);
                getCurrentProcess = linker.downcallHandle(
                        kernel32.find("GetCurrentProcess").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.ADDRESS)
                );
                prefetchVirtualMemory = linker.downcallHandle(
                        kernel32.find("PrefetchVirtualMemory").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,    // HANDLE hProcess
                                ValueLayout.JAVA_LONG,  // ULONG_PTR NumberOfEntries
                                ValueLayout.ADDRESS,    // PWIN32_MEMORY_RANGE_ENTRY
                                ValueLayout.JAVA_INT    // ULONG Flags
                        )
                );
                ok = true;
            } catch (Throwable t) {
                ZeroSeekMod.LOGGER.warn("Windows PrefetchVirtualMemory not available", t);
                if (winArena != null) {
                    try {
                        winArena.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        POSIX_MADVISE = posixMadvise;
        GET_CURRENT_PROCESS = getCurrentProcess;
        PREFETCH_VIRTUAL_MEMORY = prefetchVirtualMemory;
        WIN_ARENA = winArena;
        SUPPORTED = ok;
    }

    private MadviseHelper() {
    }

    public static boolean willNeed(MemorySegment segment) {
        if (!SUPPORTED || segment == null) {
            return false;
        }
        if (IS_LINUX) {
            return posixAdvise(segment, POSIX_MADV_WILLNEED);
        }
        if (IS_WINDOWS) {
            return windowsPrefetch(segment);
        }
        return false;
    }

    public static boolean dontNeed(MemorySegment segment) {
        if (!SUPPORTED || segment == null) {
            return false;
        }
        if (IS_LINUX) {
            return posixAdvise(segment, POSIX_MADV_DONTNEED);
        }
        // Windows has no simple equivalent for DONTNEED; leave pages to OS.
        return false;
    }

    private static boolean posixAdvise(MemorySegment segment, int advice) {
        try {
            int ret = (int) POSIX_MADVISE.invokeExact(segment, segment.byteSize(), advice);
            return ret == 0;
        } catch (Throwable t) {
            ZeroSeekMod.LOGGER.warn("posix_madvise failed", t);
            return false;
        }
    }

    private static boolean windowsPrefetch(MemorySegment segment) {
        if (WIN_ARENA == null || GET_CURRENT_PROCESS == null || PREFETCH_VIRTUAL_MEMORY == null) {
            return false;
        }
        try {
            MemorySegment entry = WIN_ARENA.allocate(WIN32_MEMORY_RANGE_ENTRY);
            entry.set(
                    ValueLayout.ADDRESS,
                    WIN32_MEMORY_RANGE_ENTRY.byteOffset(MemoryLayout.PathElement.groupElement("VirtualAddress")),
                    MemorySegment.ofAddress(segment.address())
            );
            entry.set(
                    ValueLayout.JAVA_LONG,
                    WIN32_MEMORY_RANGE_ENTRY.byteOffset(MemoryLayout.PathElement.groupElement("NumberOfBytes")),
                    segment.byteSize()
            );

            MemorySegment handle = (MemorySegment) GET_CURRENT_PROCESS.invokeExact();
            int ret = (int) PREFETCH_VIRTUAL_MEMORY.invokeExact(handle, 1L, entry, 0);
            return ret != 0;
        } catch (Throwable t) {
            ZeroSeekMod.LOGGER.warn("PrefetchVirtualMemory failed", t);
            return false;
        }
    }

    public static boolean supported() {
        return SUPPORTED;
    }
}
