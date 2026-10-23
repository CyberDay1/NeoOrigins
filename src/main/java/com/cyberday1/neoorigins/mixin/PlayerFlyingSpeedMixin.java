package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.LikeAirPower;
import com.cyberday1.neoorigins.power.capability.PowerCapabilities;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code neoorigins:like_air}: airborne control speed follows the player's
 * movement speed modifiers. {@code Player.getFlyingSpeed} is what
 * {@code LivingEntity.getFrictionInfluencedSpeed} returns off the ground, the
 * same value Apoli's {@code modify_air_speed} changes. Runs on both sides, because
 * the local player predicts its own movement; the capability check resolves
 * through the synced client mirror there.
 */
@Mixin(Player.class)
public abstract class PlayerFlyingSpeedMixin {

    @Inject(method = "getFlyingSpeed", at = @At("RETURN"), cancellable = true)
    private void neoorigins$likeAir(CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        if (self.getAbilities().flying) return;
        if (!PowerCapabilities.hasActive(self, LikeAirPower.CAPABILITY)) return;
        cir.setReturnValue(LikeAirPower.transfer(cir.getReturnValue(), self.getAttribute(Attributes.MOVEMENT_SPEED)));
    }
}
