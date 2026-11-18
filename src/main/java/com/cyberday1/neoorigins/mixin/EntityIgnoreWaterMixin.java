package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.capability.PowerCapabilities;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Suppresses water-current pushing for players with the {@code ignore_water}
 * capability (emitted by {@link com.cyberday1.neoorigins.power.builtin.IgnoreWaterPower}).
 *
 * <p>Answers no to the per-type {@code isPushedByFluid(FluidType)} the body scan
 * asks before adding a fluid's flow, for water only. This used to inject into
 * {@code Entity.isPushedByFluid()}, which {@code Player} overrides without
 * calling super, so it never reached a player. Common mixin, both logical sides
 * from the synced tag: the client predicts its own movement.
 *
 * <p>Movement speed in water is handled separately via a
 * {@code water_movement_efficiency} attribute modifier on the power itself.
 */
@Mixin(Entity.class)
public abstract class EntityIgnoreWaterMixin {

    @WrapOperation(
        method = "updateFluidHeightAndDoFluidPushing()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;isPushedByFluid(Lnet/neoforged/neoforge/fluids/FluidType;)Z"
        )
    )
    private boolean neoorigins$ignoreWaterPushing(Entity self, FluidType type, Operation<Boolean> original) {
        if (type == NeoForgeMod.WATER_TYPE.value() && self instanceof Player player
                && PowerCapabilities.hasActive(player, "ignore_water")) {
            return false;
        }
        return original.call(self, type);
    }
}
