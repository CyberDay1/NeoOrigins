package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.capability.PowerCapabilities;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Short-circuits EnderMan.isLookingAtMe for players carrying the
 * {@code "ender_gaze"} capability so they can look at Endermen without
 * provoking them. Mirrors vanilla's "wearing a carved pumpkin" escape
 * hatch but driven by power capability, no helmet slot required.
 *
 * <p>Used by Enderian's Ender Eyes power via EnderGazeImmunityPower.
 */
@Mixin(EnderMan.class)
public class EnderManLookMixin {

    @Inject(method = "isLookingAtMe", at = @At("HEAD"), cancellable = true)
    private void neoorigins$enderGazeImmunity(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (PowerCapabilities.hasActive(player, "ender_gaze")) {
            cir.setReturnValue(false);
        }
    }
}
