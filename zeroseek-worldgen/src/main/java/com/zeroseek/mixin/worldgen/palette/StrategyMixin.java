package com.zeroseek.mixin.worldgen.palette;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.zeroseek.worldgen.palette.ZeroSeekHashPalette;
import net.minecraft.world.level.chunk.Configuration;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.Strategy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Strategy.class)
public abstract class StrategyMixin {

    @Unique
    private static final Palette.Factory HASH = ZeroSeekHashPalette::create;

    @Unique
    private static final Configuration HASH_3BIT = new Configuration.Simple(HASH, 3);
    @Unique
    private static final Configuration HASH_4BIT = new Configuration.Simple(HASH, 4);
    @Unique
    private static final Configuration HASH_5BIT = new Configuration.Simple(HASH, 5);
    @Unique
    private static final Configuration HASH_6BIT = new Configuration.Simple(HASH, 6);
    @Unique
    private static final Configuration HASH_7BIT = new Configuration.Simple(HASH, 7);
    @Unique
    private static final Configuration HASH_8BIT = new Configuration.Simple(HASH, 8);

    @Mixin(targets = "net/minecraft/world/level/chunk/Strategy$1")
    static class Strategy1Mixin<T> {

        @ModifyExpressionValue(
            method = "getConfigurationForBitCount(I)Lnet/minecraft/world/level/chunk/Configuration;",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/chunk/Strategy;FOUR_BITS_LINEAR:Lnet/minecraft/world/level/chunk/Configuration;")
        )
        private Configuration getHash4Bit(Configuration original, @Local(argsOnly = true) int bits) {
            if (bits == 3 || bits == 4) {
                return HASH_4BIT;
            }
            return original;
        }

        @ModifyExpressionValue(
            method = "getConfigurationForBitCount(I)Lnet/minecraft/world/level/chunk/Configuration;",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/chunk/Strategy;FIVE_BITS_HASHMAP:Lnet/minecraft/world/level/chunk/Configuration;")
        )
        private Configuration getHash5Bit(Configuration original) {
            return HASH_5BIT;
        }

        @ModifyExpressionValue(
            method = "getConfigurationForBitCount(I)Lnet/minecraft/world/level/chunk/Configuration;",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/chunk/Strategy;SIX_BITS_HASHMAP:Lnet/minecraft/world/level/chunk/Configuration;")
        )
        private Configuration getHash6Bit(Configuration original) {
            return HASH_6BIT;
        }

        @ModifyExpressionValue(
            method = "getConfigurationForBitCount(I)Lnet/minecraft/world/level/chunk/Configuration;",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/chunk/Strategy;SEVEN_BITS_HASHMAP:Lnet/minecraft/world/level/chunk/Configuration;")
        )
        private Configuration getHash7Bit(Configuration original) {
            return HASH_7BIT;
        }

        @ModifyExpressionValue(
            method = "getConfigurationForBitCount(I)Lnet/minecraft/world/level/chunk/Configuration;",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/chunk/Strategy;EIGHT_BITS_HASHMAP:Lnet/minecraft/world/level/chunk/Configuration;")
        )
        private Configuration getHash8Bit(Configuration original) {
            return HASH_8BIT;
        }
    }

    @Mixin(targets = "net/minecraft/world/level/chunk/Strategy$2")
    static class Strategy2Mixin<T> {

        @ModifyExpressionValue(
            method = "getConfigurationForBitCount(I)Lnet/minecraft/world/level/chunk/Configuration;",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/chunk/Strategy;THREE_BITS_LINEAR:Lnet/minecraft/world/level/chunk/Configuration;")
        )
        private Configuration getHash3Bit(Configuration original) {
            return HASH_3BIT;
        }
    }
}
