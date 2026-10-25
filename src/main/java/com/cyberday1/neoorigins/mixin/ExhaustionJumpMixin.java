package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.ExhaustionFilterPower;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@code exhaustion_filter} source {@code jump}: both charges in {@code Player.jumpFromGround}. */
@Mixin(Player.class)
public abstract class ExhaustionJumpMixin {

    @WrapOperation(method = "jumpFromGround", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private void neoorigins$filterJumpExhaustion(Player player, float amount, Operation<Void> original) {
        if (ExhaustionFilterPower.filters(player, "jump")) return;
        original.call(player, amount);
    }
}
