package com.zeroseek.worldgen.lock;

import net.minecraft.world.level.ChunkPos;

/**
 * Immutable spatial lock token identifying chunk coordinates and usage type.
 *
 * @param chunkPos Packed chunk position via ChunkPos.asLong(x, z).
 * @param usage    Lock usage mode (read/write).
 */
public record SpatialLockToken(long chunkPos, LockUsage usage) {

    /**
     * Lock usage mode for worldgen spatial locking.
     */
    public enum LockUsage {
        WORLDGEN_WRITE,
        WORLDGEN_READ
    }

    /**
     * Factory helper to create a token from discrete chunk coordinates.
     *
     * @param chunkX Chunk X coordinate.
     * @param chunkZ Chunk Z coordinate.
     * @param usage  Lock usage mode.
     * @return A new {@link SpatialLockToken}.
     */
    public static SpatialLockToken of(int chunkX, int chunkZ, LockUsage usage) {
        return new SpatialLockToken(ChunkPos.asLong(chunkX, chunkZ), usage);
    }

    /**
     * Factory helper to create a token from a {@link ChunkPos}.
     *
     * @param pos   Chunk position.
     * @param usage Lock usage mode.
     * @return A new {@link SpatialLockToken}.
     */
    public static SpatialLockToken of(ChunkPos pos, LockUsage usage) {
        return new SpatialLockToken(pos.toLong(), usage);
    }

    /**
     * Returns the chunk X coordinate unpacked from {@code chunkPos}.
     */
    public int chunkX() {
        return ChunkPos.getX(chunkPos);
    }

    /**
     * Returns the chunk Z coordinate unpacked from {@code chunkPos}.
     */
    public int chunkZ() {
        return ChunkPos.getZ(chunkPos);
    }

    /**
     * Converts to a Minecraft {@link ChunkPos}.
     */
    public ChunkPos toChunkPos() {
        return new ChunkPos(chunkPos);
    }
}
