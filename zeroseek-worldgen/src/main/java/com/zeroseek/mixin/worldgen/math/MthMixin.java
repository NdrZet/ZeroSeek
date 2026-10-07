package com.zeroseek.mixin.worldgen.math;

import com.zeroseek.worldgen.math.CompactSineLUT;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mth.class)
public class MthMixin {

    @Shadow
    @Final
    @Mutable
    public static float[] SIN;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void onClassInit(CallbackInfo ci) {
        CompactSineLUT.init();
        MthMixin.SIN = null;
    }

    /**
     * @author ZeroSeek
     * @reason Delegate to CompactSineLUT for L1/L2 cache locality
     */
    @Overwrite
    public static float sin(double d) {
        return CompactSineLUT.sin(d);
    }

    /**
     * @author ZeroSeek
     * @reason Delegate to CompactSineLUT for L1/L2 cache locality
     */
    @Overwrite
    public static float cos(double d) {
        return CompactSineLUT.cos(d);
    }
}
