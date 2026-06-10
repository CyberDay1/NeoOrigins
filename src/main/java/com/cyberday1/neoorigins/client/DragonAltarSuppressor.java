package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.DragonSurvivalCompat;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Suppresses the dragon-type selection screens that Dragon Survival pushes on
 * players, so NeoOrigins' origin picker is the single entry point for becoming a
 * dragon. When DS is installed, this cancels the opening of its
 * {@code DragonAltarScreen}/{@code DragonEditorScreen} (the forced ~5s-after-join
 * prompt and the inventory-tab/altar-block entries alike).
 *
 * <p>Matches on the screen's simple class name rather than a hard type reference
 * so no DS class is loaded when DS is absent, and so it is resilient to DS moving
 * the class between packages. Only active when {@code dragonsurvival} is loaded.
 *
 * <p>Server-side belt-and-suspenders (optional, operator-set) is the DS config
 * {@code start_with_dragon_choice = false}; this client guard works regardless.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = NeoOrigins.MOD_ID)
public final class DragonAltarSuppressor {

    private DragonAltarSuppressor() {}

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!DragonSurvivalCompat.isLoaded()) return;
        Screen next = event.getNewScreen();
        if (next == null) return;
        String name = next.getClass().getSimpleName();
        if (name.equals("DragonAltarScreen") || name.equals("DragonEditorScreen")) {
            event.setCanceled(true);
        }
    }
}
