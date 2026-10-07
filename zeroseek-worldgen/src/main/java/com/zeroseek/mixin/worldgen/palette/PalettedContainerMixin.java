package com.zeroseek.mixin.worldgen.palette;

import com.zeroseek.worldgen.palette.CompactingPackedIntegerArray;
import com.zeroseek.worldgen.palette.ZeroSeekHashPalette;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.chunk.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

/**
 * Optimized PalettedContainer implementation:
 * - ThreadLocal cached short arrays to crush GC allocation spikes during compaction.
 * - Array cloning when palette size doesn't change.
 * - Array-based block counting instead of HashMap instantiation.
 */
@Mixin(PalettedContainer.class)
public abstract class PalettedContainerMixin<T> {
    private static final ThreadLocal<short[]> CACHED_ARRAY_4096 = ThreadLocal.withInitial(() -> new short[4096]);
    private static final ThreadLocal<short[]> CACHED_ARRAY_64 = ThreadLocal.withInitial(() -> new short[64]);

    @Shadow
    public abstract void acquire();

    @Shadow
    protected abstract T get(int index);

    @Shadow
    private volatile PalettedContainer.Data<T> data;

    @Shadow
    public abstract void release();

    /**
     * @reason Optimize chunk serialization and palette compaction without GC allocation storm
     * @author ZeroSeek
     */
    @Overwrite
    public PalettedContainerRO.PackedData<T> pack(Strategy<T> strategy) {
        this.acquire();

        ZeroSeekHashPalette<T> hashPalette = null;
        Optional<LongStream> dataStream = Optional.empty();
        List<T> elements = null;

        final Palette<T> palette = this.data.palette();
        final BitStorage storage = this.data.storage();
        if (storage instanceof ZeroBitStorage || palette.getSize() == 1) {
            elements = List.of(palette.valueFor(0));
        } else if (palette instanceof ZeroSeekHashPalette<T> zeroSeekHashPalette) {
            hashPalette = zeroSeekHashPalette;
        }

        if (elements == null) {
            ZeroSeekHashPalette<T> compactedPalette = new ZeroSeekHashPalette<>(storage.getBits());
            short[] array = this.getOrCreate(strategy.entryCount());

            ((CompactingPackedIntegerArray) storage).zeroseek$compact(this.data.palette(), compactedPalette, array);

            Configuration origConfig;
            if (hashPalette != null && hashPalette.getSize() == compactedPalette.getSize() &&
                    !(origConfig = ((StrategyAccessor) strategy).zeroseek$getConfigurationForPaletteSize(hashPalette.getSize())).alwaysRepack() && storage.getBits() == origConfig.bitsInStorage()) {
                dataStream = this.asOptional(storage.getRaw().clone());
                elements = hashPalette.getElements();
            } else {
                int bits = ((StrategyAccessor) strategy).zeroseek$getConfigurationForPaletteSize(compactedPalette.getSize()).bitsInStorage();
                if (bits != 0) {
                    SimpleBitStorage copy = new SimpleBitStorage(bits, array.length);
                    for (int i = 0; i < array.length; ++i) {
                        copy.set(i, array[i]);
                    }
                    dataStream = this.asOptional(copy.getRaw());
                }

                elements = compactedPalette.getElements();
            }
        }

        this.release();
        return new PalettedContainerRO.PackedData<>(elements, dataStream);
    }

    private Optional<LongStream> asOptional(long[] raw) {
        return Optional.of(Arrays.stream(raw));
    }

    private short[] getOrCreate(int size) {
        return switch (size) {
            case 64 -> CACHED_ARRAY_64.get();
            case 4096 -> CACHED_ARRAY_4096.get();
            default -> new short[size];
        };
    }

    /**
     * @reason Array-based block counting instead of HashMap instantiation to reduce GC pressure
     * @author ZeroSeek
     */
    @Inject(method = "count(Lnet/minecraft/world/level/chunk/PalettedContainer$CountConsumer;)V", at = @At("HEAD"), cancellable = true)
    public void count(PalettedContainer.CountConsumer<T> consumer, CallbackInfo ci) {
        int len = this.data.palette().getSize();
        if (len > 4096) {
            return;
        }

        short[] counts = new short[len];
        this.data.storage().getAll(i -> counts[i]++);

        for (int i = 0; i < counts.length; i++) {
            T obj = this.data.palette().valueFor(i);
            if (obj != null) {
                consumer.accept(obj, counts[i]);
            }
        }

        ci.cancel();
    }
}
