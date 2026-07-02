package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.DragonSurvivalCompat;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Optionally suppresses Dragon Survival's own dragon-<b>species</b> selection
 * screens so NeoOrigins' origin picker can be the single entry point for
 * <em>becoming</em> a dragon. <b>Off by default</b>: DS's species screens
 * (altar / species popup) stay usable alongside origins unless the client config
 * {@code compat.suppress_dragon_species_screens} is enabled. When enabled, this
 * cancels the opening of {@code DragonAltarScreen} and the newer
 * {@code DragonSpeciesScreen}.
 *
 * <p><b>The dragon customization editor is NEVER suppressed.</b>
 * {@code DragonEditorScreen} (DS package {@code ...screens.dragon_editor}) is the
 * appearance/skin editor a player opens deliberately via {@code /dragon editor}
 * or the dragon inventory tab; it has no NeoOrigins equivalent, so cancelling it
 * just removed working DS functionality (players could never open the editor,
 * even by command). Likewise skins, abilities and the dragon inventory are left
 * alone — only the species pickers are ever intercepted, and only when opted in.
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
        if (!NeoOriginsClientConfig.isSuppressDragonSpeciesScreens()) return;
        Screen next = event.getNewScreen();
        if (next == null) return;
        String name = next.getClass().getSimpleName();
        // Only the species-choice UIs. NOT DragonEditorScreen (the appearance
        // editor) — origins replaces choosing a species, not customizing one.
        if (name.equals("DragonAltarScreen") || name.equals("DragonSpeciesScreen")) {
            event.setCanceled(true);
        }
    }
}
