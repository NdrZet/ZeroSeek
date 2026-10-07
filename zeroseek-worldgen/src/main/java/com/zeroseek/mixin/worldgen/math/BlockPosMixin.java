package com.zeroseek.mixin.worldgen.math;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Inlines offset methods directly in BlockPos to assist Hotspot JIT compilation.
 */
@Mixin(BlockPos.class)
public abstract class BlockPosMixin extends Vec3i {

    public BlockPosMixin(int x, int y, int z) {
        super(x, y, z);
    }

    /**
     * @author ZeroSeek
     * @reason Direct inlining without Vec3i delegation
     */
    @Override
    @Overwrite
    public BlockPos above() {
        return new BlockPos(this.getX(), this.getY() + 1, this.getZ());
    }

    /**
     * @author ZeroSeek
     * @reason Direct inlining without Vec3i delegation
     */
    @Override
    @Overwrite
    public BlockPos above(int distance) {
        return new BlockPos(this.getX(), this.getY() + distance, this.getZ());
    }

    /**
     * @author ZeroSeek
     * @reason Direct inlining without Vec3i delegation
     */
    @Override
    @Overwrite
    public BlockPos below() {
        return new BlockPos(this.getX(), this.getY() - 1, this.getZ());
    }

    /**
     * @author ZeroSeek
     * @reason Direct inlining without Vec3i delegation
     */
    @Override
    @Overwrite
    public BlockPos below(int distance) {
        return new BlockPos(this.getX(), this.getY() - distance, this.getZ());
    }

    /**
     * @author ZeroSeek
     * @reason Direct inlining without Vec3i delegation
     */
    @Overwrite
    public BlockPos north() {
        return new BlockPos(this.getX(), this.getY(), this.getZ() - 1);
    }

    /**
     * @author ZeroSeek
     * @reason Direct inlining without Vec3i delegation
     */
    @Overwrite
    public BlockPos north(int distance) {
        return new BlockPos(this.getX(), this.getY(), this.getZ() - distance);
    }

    /**
     * @author ZeroSeek
     * @reason Direct inlining without Vec3i delegation
     */
    @Overwrite
    public BlockPos south() {
        return new BlockPos(this.getX(), this.getY(), this.getZ() + 1);
    }

    /**
     * @author ZeroSeek
     * @reason Direct inlining without Vec3i delegation
     */
    @Overwrite
    public BlockPos south(int distance) {
        return new BlockPos(this.getX(), this.getY(), this.getZ() + distance);
    }

    /**
     * @author ZeroSeek
     * @reason Direct inlining without Vec3i delegation
     */
    @Overwrite
    public BlockPos west() {
        return new BlockPos(this.getX() - 1, this.getY(), this.getZ());
    }

    /**
     * @author ZeroSeek
     * @reason Direct inlining without Vec3i delegation
     */
    @Overwrite
    public BlockPos west(int distance) {
        return new BlockPos(this.getX() - distance, this.getY(), this.getZ());
    }

    /**
     * @author ZeroSeek
     * @reason Direct inlining without Vec3i delegation
     */
    @Overwrite
    public BlockPos east() {
        return new BlockPos(this.getX() + 1, this.getY(), this.getZ());
    }

    /**
     * @author ZeroSeek
     * @reason Direct inlining without Vec3i delegation
     */
    @Overwrite
    public BlockPos east(int distance) {
        return new BlockPos(this.getX() + distance, this.getY(), this.getZ());
    }
}
