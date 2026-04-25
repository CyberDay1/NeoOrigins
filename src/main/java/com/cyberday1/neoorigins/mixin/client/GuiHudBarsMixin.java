package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.NeoOriginsConfig;
import com.cyberday1.neoorigins.client.ClientActivePowers;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the hunger and air HUD bars for origins that don't consume them.
 *
 * <p>Driven by two capability tags published by {@code HideHudBarPower}:
 * {@code hide_hunger_bar} skips {@code renderFoodLevel}, {@code hide_air_bar}
 * skips {@code renderAirLevel}. The global {@code hideHudBars} common-config
 * flag gates both — pack authors can turn the feature off entirely without
 * editing every origin JSON.
 */
@Mixin(Gui.class)
public abstract class GuiHudBarsMixin {

    @Inject(method = "renderFoodLevel", at = @At("HEAD"), cancellable = true)
    private void neoorigins$maybeHideFood(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (!NeoOriginsConfig.isHideHudBarsEnabled()) return;
        if (ClientActivePowers.hasCapability("hide_hunger_bar")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderAirLevel", at = @At("HEAD"), cancellable = true)
    private void neoorigins$maybeHideAir(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (!NeoOriginsConfig.isHideHudBarsEnabled()) return;
        if (ClientActivePowers.hasCapability("hide_air_bar")) {
            ci.cancel();
            return;
        }
        // Aquatic origins (breath_out_of_fluid → "dries_out_of_water"
        // capability) keep air at max while submerged, so the bubble row only
        // ever shows "FULL" underwater — pointless visual clutter. Hide it
        // while in water; out of water the row still renders so dry-out
        // depletion remains visible.
        if (ClientActivePowers.hasCapability("dries_out_of_water")) {
            var localPlayer = net.minecraft.client.Minecraft.getInstance().player;
            if (localPlayer != null && localPlayer.isInWater()) {
                ci.cancel();
            }
        }
    }
}
