package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.ExhaustionFilterPower;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@code exhaustion_filter} source {@code mining}: the 0.005 charged in {@code Block.playerDestroy}, whose player is a ServerPlayer on 26.3. */
@Mixin(Block.class)
public abstract class ExhaustionMiningMixin {

    @WrapOperation(method = "playerDestroy", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V"))
    private void neoorigins$filterMiningExhaustion(ServerPlayer player, float amount, Operation<Void> original) {
        if (ExhaustionFilterPower.filters(player, "mining")) return;
        original.call(player, amount);
    }
}
