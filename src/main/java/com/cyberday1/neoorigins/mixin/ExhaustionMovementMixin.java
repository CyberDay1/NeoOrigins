package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.ExhaustionFilterPower;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code exhaustion_filter} sources {@code sprint} and {@code swim}: the distance
 * charges in {@code ServerPlayer.checkMovementStatistics}. The method picks one
 * branch per move in the order swim, underwater, wading, climb, ground; the source
 * is read back from the same state, so a wrapped call is attributed to the branch
 * that made it. Walking and crouching charge 0 and are left alone.
 */
@Mixin(ServerPlayer.class)
public abstract class ExhaustionMovementMixin {

    @WrapOperation(method = "checkMovementStatistics", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V"))
    private void neoorigins$filterMovementExhaustion(ServerPlayer player, float amount, Operation<Void> original) {
        String source = player.isSwimming() || player.isEyeInFluid(FluidTags.WATER) || player.isInWater()
            ? "swim"
            : player.isSprinting() ? "sprint" : null;
        if (source != null && ExhaustionFilterPower.filters(player, source)) return;
        original.call(player, amount);
    }
}
