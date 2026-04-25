package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.capability.PowerCapabilities;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Short-circuits EnderMan's stare-detection check for players carrying the
 * {@code "ender_gaze"} capability so they can look at Endermen without
 * provoking them. Mirrors vanilla's "wearing a carved pumpkin" escape
 * hatch but driven by power capability, no helmet slot required.
 *
 * <p>Method name differs by version: 1.21.1 has {@code isLookingAtMe(Player)},
 * 26.1 renamed it to {@code isBeingStaredBy(Player)}. This branch targets
 * the 26.1 name; the 1.21.1 branch targets the older name.
 *
 * <p>Used by Enderian's Ender Eyes power via EnderGazeImmunityPower.
 */
@Mixin(EnderMan.class)
public class EnderManLookMixin {

    // 26.1 Mojang renamed isLookingAtMe(Player) → isBeingStaredBy(Player) and
    // refactored EnderMan's stare-tracking onto a synced DATA_STARED_AT field
    // (hasBeenStaredAt / setBeingStaredAt). The check is now private but
    // mixins can still target private methods. Same signature (Player → bool).
    // require = 0 kept as belt-and-suspenders against future renames.
    @Inject(method = "isBeingStaredBy", at = @At("HEAD"), cancellable = true, require = 0)
    private void neoorigins$enderGazeImmunity(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (PowerCapabilities.hasActive(player, "ender_gaze")) {
            cir.setReturnValue(false);
        }
    }
}
