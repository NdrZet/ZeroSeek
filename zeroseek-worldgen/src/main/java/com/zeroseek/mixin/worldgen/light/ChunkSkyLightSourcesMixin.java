package com.zeroseek.mixin.worldgen.light;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Accelerates ChunkSkyLightSources initialization by leveraging pre-computed MOTION_BLOCKING heightmaps.
 * Eliminates up to 50,000 redundant block lookups through empty air columns per chunk.
 */
@Mixin(ChunkSkyLightSources.class)
public abstract class ChunkSkyLightSourcesMixin {

    @Shadow @Final private int minY;
    @Shadow @Final private BlockPos.MutableBlockPos mutablePos1;
    @Shadow @Final private BlockPos.MutableBlockPos mutablePos2;
    @Shadow private static boolean isEdgeOccluded(BlockState upper, BlockState lower) { throw new AssertionError(); }

    @Inject(
            method = "findLowestSourceY",
            at = @At("HEAD"),
            cancellable = true
    )
    private void zeroseek$fastFindLowestSourceY(ChunkAccess chunk, int highestSectionIndex, int x, int z, CallbackInfoReturnable<Integer> cir) {
        if (!chunk.hasPrimedHeightmap(Heightmap.Types.MOTION_BLOCKING)) {
            return;
        }

        int topY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (topY <= this.minY) {
            cir.setReturnValue(this.minY);
            return;
        }

        int targetSectionIdx = chunk.getSectionIndex(topY);
        if (targetSectionIdx < 0) {
            cir.setReturnValue(this.minY);
            return;
        }

        // Set position pointers directly around topY
        int checkY = topY + 1;
        this.mutablePos1.set(x, checkY, z);
        this.mutablePos2.setWithOffset(this.mutablePos1, Direction.DOWN);

        BlockState upperState = Blocks.AIR.defaultBlockState();

        // Scan downwards only from the target section
        for (int sIdx = targetSectionIdx; sIdx >= 0; --sIdx) {
            LevelChunkSection section = chunk.getSection(sIdx);
            if (section.hasOnlyAir()) {
                upperState = Blocks.AIR.defaultBlockState();
                int secY = chunk.getSectionYFromSectionIndex(sIdx);
                this.mutablePos1.setY(SectionPos.sectionToBlockCoord(secY));
                this.mutablePos2.setY(this.mutablePos1.getY() - 1);
                continue;
            }

            int startLocalY = (sIdx == targetSectionIdx) ? (topY & 15) : 15;
            for (int localY = startLocalY; localY >= 0; --localY) {
                BlockState lowerState = section.getBlockState(x, localY, z);
                if (isEdgeOccluded(upperState, lowerState)) {
                    cir.setReturnValue(this.mutablePos1.getY());
                    return;
                }
                upperState = lowerState;
                this.mutablePos1.set(this.mutablePos2);
                this.mutablePos2.move(Direction.DOWN);
            }
        }

        cir.setReturnValue(this.minY);
    }
}
