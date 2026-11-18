package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.capability.PowerCapabilities;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Suppresses water-current pushing for players with the {@code ignore_water}
 * capability (emitted by {@link com.cyberday1.neoorigins.power.builtin.IgnoreWaterPower}).
 *
 * <p>{@code EntityFluidInteraction.applyCurrentTo} asks the entity's per-type
 * {@code isPushedByFluid(FluidType)} before adding a fluid's current, in the
 * whole-entity overload and the per-fluid one; this answers no for water. It used to inject into {@code Entity.isPushedByFluid()},
 * which {@code Player} overrides without calling super, so it never reached a
 * player. Common mixin, both logical sides from the synced tag: the client
 * predicts its own movement.
 *
 * <p>Movement speed in water is handled separately via a
 * {@code water_movement_efficiency} attribute modifier on the power itself.
 */
@Mixin(EntityFluidInteraction.class)
public abstract class EntityIgnoreWaterMixin {

    @WrapOperation(
        method = {
            "applyCurrentTo(Lnet/minecraft/world/entity/Entity;)V",
            "applyCurrentTo(Lnet/neoforged/neoforge/fluids/FluidType;Lnet/minecraft/world/entity/Entity;D)V"
        },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;isPushedByFluid(Lnet/neoforged/neoforge/fluids/FluidType;)Z"
        )
    )
    private boolean neoorigins$ignoreWaterPushing(Entity entity, FluidType type, Operation<Boolean> original) {
        if (type == NeoForgeMod.WATER_TYPE.value() && entity instanceof Player player
                && PowerCapabilities.hasActive(player, "ignore_water")) {
            return false;
        }
        return original.call(entity, type);
    }
}
