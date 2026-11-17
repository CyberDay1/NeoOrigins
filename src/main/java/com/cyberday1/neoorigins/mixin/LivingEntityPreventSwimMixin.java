package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.PreventActionPower;
import com.cyberday1.neoorigins.power.capability.PowerCapabilities;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code prevent_action} {@code swim}, in water:
 *
 * <ul>
 *   <li>Holding jump in deep water no longer swims the player upward.
 *       {@code aiStep} calls {@code jumpInFluid} only once the fluid is deeper
 *       than the jump threshold; in shallower water it still takes the ordinary
 *       ground jump, so the player can hop out of a puddle or up a step. Lava and
 *       other fluids keep their swim-up, so deep lava can't trap the player.</li>
 *   <li>Sprinting no longer makes the player hover. Out of the swim pose the
 *       client only drops sprint at the surface, and
 *       {@code getFluidFallingAdjustedMovement} skips water's gravity while
 *       sprinting, so it reads as not sprinting here and the player sinks as when
 *       walking. Sprint itself stays, for land sprint speed on the bottom.</li>
 * </ul>
 *
 * <p>Both logical sides, from the synced {@link PreventActionPower#PREVENT_SWIM}
 * tag, for the same reason as {@link EntityPreventSwimMixin}.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPreventSwimMixin {

    @WrapOperation(
        method = "aiStep()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;jumpInFluid(Lnet/neoforged/neoforge/fluids/FluidType;)V"
        )
    )
    private void neoorigins$preventSwimUp(LivingEntity self, FluidType type,
                                          Operation<Void> original) {
        if (type == NeoForgeMod.WATER_TYPE.value() && self instanceof Player player
                && PowerCapabilities.hasActive(player, PreventActionPower.PREVENT_SWIM)) {
            return;
        }
        original.call(self, type);
    }

    @WrapOperation(
        method = "getFluidFallingAdjustedMovement(DZLnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isSprinting()Z"
        )
    )
    private boolean neoorigins$sinkWhileSprinting(LivingEntity self, Operation<Boolean> original) {
        if (self instanceof Player player && self.isInWater()
                && PowerCapabilities.hasActive(player, PreventActionPower.PREVENT_SWIM)) {
            return false;
        }
        return original.call(self);
    }
}
