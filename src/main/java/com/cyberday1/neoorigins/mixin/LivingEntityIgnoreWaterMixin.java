package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.capability.PowerCapabilities;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Suppresses falling in water for players with the {@code ignore_water}
 * capability (emitted by {@link com.cyberday1.neoorigins.power.builtin.IgnoreWaterPower}).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityIgnoreWaterMixin {

    @ModifyReturnValue(method = "getFluidFallingAdjustedMovement", at = @At("RETURN"), require = 0)
    private Vec3 neoorigins$modifyFluidFallingAdjustedMovement(Vec3 original, double gravity, boolean isFalling, Vec3 deltaMovement) {
        if ((Object) this instanceof Player player) {
            if (PowerCapabilities.hasActive(player, "ignore_water")) {
                if (Math.abs(deltaMovement.y - gravity / 16.0D) < 0.025D) {
                    return new Vec3(original.x, 0, original.z);
                }
            }
        }
        return original;
    }
}
