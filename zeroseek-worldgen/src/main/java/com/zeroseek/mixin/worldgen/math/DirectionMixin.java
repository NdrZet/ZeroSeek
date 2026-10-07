package com.zeroseek.mixin.worldgen.math;

import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Direction.class)
public class DirectionMixin {
    private int zeroseek$offsetX;
    private int zeroseek$offsetY;
    private int zeroseek$offsetZ;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void zeroseek$onInit(String enumName, int ordinal, int id, int idOpposite, int idHorizontal, String name, Direction.AxisDirection direction, Direction.Axis axis, Vec3i vector, CallbackInfo ci) {
        this.zeroseek$offsetX = vector.getX();
        this.zeroseek$offsetY = vector.getY();
        this.zeroseek$offsetZ = vector.getZ();
    }

    /**
     * @author ZeroSeek
     * @reason Hoist offset fields to avoid indirection
     */
    @Overwrite
    public int getStepX() {
        return this.zeroseek$offsetX;
    }

    /**
     * @author ZeroSeek
     * @reason Hoist offset fields to avoid indirection
     */
    @Overwrite
    public int getStepY() {
        return this.zeroseek$offsetY;
    }

    /**
     * @author ZeroSeek
     * @reason Hoist offset fields to avoid indirection
     */
    @Overwrite
    public int getStepZ() {
        return this.zeroseek$offsetZ;
    }
}
