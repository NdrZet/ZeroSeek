package com.zeroseek.mixin.worldgen;

import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Thread-safety hardening for StrongholdPieces$PieceWeight adapted from C2ME.
 * Isolates mutable placeCount field per-thread to prevent race conditions during concurrent world generation.
 */
@Mixin(StrongholdPieces.PieceWeight.class)
public class StrongholdPieceWeightMixin {

    @Unique
    private final ThreadLocal<Integer> zeroseek$placeCountThreadLocal = ThreadLocal.withInitial(() -> 0);

    @Dynamic
    @Redirect(
            method = "*",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/levelgen/structure/structures/StrongholdPieces$PieceWeight;placeCount:I",
                    opcode = Opcodes.GETFIELD
            )
    )
    private int zeroseek$redirectGetPlaceCount(StrongholdPieces.PieceWeight pieceWeight) {
        return this.zeroseek$placeCountThreadLocal.get();
    }

    @SuppressWarnings("MixinAnnotationTarget")
    @Dynamic
    @Redirect(
            method = "*",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/levelgen/structure/structures/StrongholdPieces$PieceWeight;placeCount:I",
                    opcode = Opcodes.PUTFIELD
            ),
            require = 0,
            expect = 0
    )
    private void zeroseek$redirectSetPlaceCount(StrongholdPieces.PieceWeight pieceWeight, int value) {
        if (value == 0) {
            this.zeroseek$placeCountThreadLocal.remove();
        } else {
            this.zeroseek$placeCountThreadLocal.set(value);
        }
    }

    @Unique
    public ThreadLocal<Integer> zeroseek$getPlaceCountThreadLocal() {
        return this.zeroseek$placeCountThreadLocal;
    }
}
