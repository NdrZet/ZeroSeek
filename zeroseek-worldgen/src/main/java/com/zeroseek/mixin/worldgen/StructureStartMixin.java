package com.zeroseek.mixin.worldgen;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safety hardening for StructureStart adapted from C2ME.
 * Replaces non-atomic references field mutation with AtomicInteger.
 */
@Mixin(StructureStart.class)
public class StructureStartMixin {

    private final AtomicInteger zeroseek$referencesAtomic = new AtomicInteger();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void zeroseek$initReferences(Structure structure, ChunkPos chunkPos, int references, PiecesContainer pieceContainer, CallbackInfo ci) {
        this.zeroseek$referencesAtomic.set(references);
    }

    @Dynamic
    @Redirect(
            method = "*",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/levelgen/structure/StructureStart;references:I",
                    opcode = Opcodes.GETFIELD
            )
    )
    private int zeroseek$redirectGetReferences(StructureStart structureStart) {
        return this.zeroseek$referencesAtomic.get();
    }

    /**
     * @author ZeroSeek
     * @reason Thread-safe atomic reference increment
     */
    @Overwrite
    public void addReference() {
        this.zeroseek$referencesAtomic.incrementAndGet();
    }

    /**
     * @author ZeroSeek
     * @reason Thread-safe atomic reference getter
     */
    @Overwrite
    public int getReferences() {
        return this.zeroseek$referencesAtomic.get();
    }
}
