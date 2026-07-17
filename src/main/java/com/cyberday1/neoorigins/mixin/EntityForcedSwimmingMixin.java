package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.capability.PowerCapabilities;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Route B {@code origins:swimming}: while the {@code "forced_swimming"}
 * capability is active, the player can enter the swimming state in ANY medium
 * (lava, air) by sprinting, exactly as in water. Mirrors Apoli's
 * {@code SwimmingPower} mixin: force the swim pose from sprint state alone,
 * then set {@code wasTouchingWater} so {@code LivingEntity.travel()} takes the
 * water-physics branch — that is what gives real swim-up/swim-down in lava.
 *
 * <p>The capability is condition-gated server-side (CompatPower.capabilities)
 * and synced to the local client, so both sides run the same physics with no
 * rubber-banding (the forced_swimming phase-gate bit resyncs runtime flips).
 */
@Mixin(Entity.class)
public abstract class EntityForcedSwimmingMixin {

    @Shadow protected boolean wasTouchingWater;

    @Shadow public abstract void setSwimming(boolean swimming);
    @Shadow public abstract boolean isSwimming();
    @Shadow public abstract boolean isSprinting();
    @Shadow public abstract boolean isPassenger();

    @Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
    private void neoorigins$forcedSwimming(CallbackInfo ci) {
        if (!((Object) this instanceof Player player)) return;
        if (!PowerCapabilities.hasActive(player, "forced_swimming")) return;
        this.setSwimming(this.isSprinting() && !this.isPassenger());
        this.wasTouchingWater = this.isSwimming();
        ci.cancel();
    }
}
