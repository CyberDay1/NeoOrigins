package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.PreventActionPower;
import com.cyberday1.neoorigins.power.capability.PowerCapabilities;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code prevent_action} {@code swim}: keeps the player out of the swim pose, and
 * so out of its speed. The pose is set in {@code updateSwimming} from the
 * sprint key, on the client as well as the server, so it has to be refused on
 * both from the synced {@link PreventActionPower#PREVENT_SWIM} tag; clearing it
 * on the server alone left the client swimming (issue #149). Water only, like
 * the swim-up block; lava never allows the pose anyway (its fluid type can't be
 * swum in).
 */
@Mixin(Entity.class)
public abstract class EntityPreventSwimMixin {

    @Shadow public abstract void setSwimming(boolean swimming);

    @Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
    private void neoorigins$preventSwim(CallbackInfo ci) {
        if ((Object) this instanceof Player player
                && player.isInWater()
                && PowerCapabilities.hasActive(player, PreventActionPower.PREVENT_SWIM)) {
            this.setSwimming(false);
            ci.cancel();
        }
    }
}
