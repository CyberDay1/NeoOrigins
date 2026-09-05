package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.client.NeoOriginsClientConfig.DragonScreenPolicy;
import com.cyberday1.neoorigins.compat.DragonSurvivalCompat;
import com.cyberday1.neoorigins.screen.PickerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import org.jetbrains.annotations.Nullable;

/**
 * Keeps Dragon Survival's dragon-<b>species</b> screens and the NeoOrigins origin
 * picker out of each other's way. Both mods open a screen at join — DS when its
 * {@code start_with_dragon_choice} is on, NeoOrigins because the server tells the
 * client to — and whichever lands second replaces the first, which is how players
 * got knocked out of the origin picker.
 *
 * <p>The default policy is {@link DragonScreenPolicy#DEFER}: DS's screen is held
 * back and reopened once the picker is done, so neither mod loses its screen.
 * {@link DragonScreenPolicy#SUPPRESS} cancels DS's species screens outright and
 * {@link DragonScreenPolicy#ALLOW} restores the old last-one-wins behaviour; see
 * the client config {@code compat.dragon_species_screens}.
 *
 * <p><b>The dragon customization editor is NEVER touched.</b>
 * {@code DragonEditorScreen} (DS package {@code ...screens.dragon_editor}) is the
 * appearance/skin editor a player opens deliberately via {@code /dragon editor}
 * or the dragon inventory tab; it has no NeoOrigins equivalent. Likewise skins,
 * abilities and the dragon inventory are left alone — only the species pickers
 * are ever intercepted, and only while our own picker is involved.
 *
 * <p>Matches on the screen's simple class name rather than a hard type reference
 * so no DS class is loaded when DS is absent, and so it is resilient to DS moving
 * the class between packages. Only active when {@code dragonsurvival} is loaded.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = NeoOrigins.MOD_ID)
public final class DragonAltarSuppressor {

    private DragonAltarSuppressor() {}

    /** DS's screen, held while the picker runs. Vanilla type: never a DS reference. */
    private static Screen deferred;
    private static boolean replayPending;

    /** What {@link #decide} concluded should happen to a screen transition. */
    enum Action {
        /** Leave the transition alone. */
        NONE,
        /** Cancel DS's screen and drop it. */
        CANCEL,
        /** Cancel DS's screen but keep it for replay after the picker. */
        CANCEL_AND_STASH,
        /** Let the picker open, keeping the DS screen it displaces for replay. */
        STASH_ONLY
    }

    /**
     * Pure decision half of the handler, split out so the state machine is
     * testable without a client. The race runs both ways: DS can open over the
     * picker, and the picker can open over DS.
     */
    static Action decide(DragonScreenPolicy policy, boolean newIsSpecies, boolean newIsPicker,
                         boolean currentIsSpecies, boolean currentIsPicker) {
        if (policy == DragonScreenPolicy.ALLOW) return Action.NONE;
        if (newIsSpecies) {
            if (policy == DragonScreenPolicy.SUPPRESS) return Action.CANCEL;
            // Only intercept a species screen that would trample the picker; one
            // opened from an altar, the inventory button or a command goes through.
            return currentIsPicker ? Action.CANCEL_AND_STASH : Action.NONE;
        }
        // The picker is server-driven and mandatory, so it always wins — but keep
        // the DS screen it replaced instead of losing it.
        if (newIsPicker && currentIsSpecies && policy == DragonScreenPolicy.DEFER) {
            return Action.STASH_ONLY;
        }
        return Action.NONE;
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!DragonSurvivalCompat.isLoaded()) return;
        Screen next = event.getNewScreen();
        if (next == null) return;
        Screen current = event.getCurrentScreen();
        Action action = decide(NeoOriginsClientConfig.dragonSpeciesScreens(),
                               isSpeciesScreen(next), next instanceof PickerScreen,
                               isSpeciesScreen(current), current instanceof PickerScreen);
        switch (action) {
            case CANCEL -> event.setCanceled(true);
            case CANCEL_AND_STASH -> {
                deferred = next;
                event.setCanceled(true);
            }
            case STASH_ONLY -> deferred = current;
            case NONE -> { }
        }
    }

    /**
     * Called from the picker's single close path. Only arms a flag: opening a
     * screen from inside a screen transition leaves the new screen uninitialised,
     * so the actual open waits for the next client tick.
     */
    public static void onPickerClosed() {
        if (deferred != null) replayPending = true;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!replayPending) return;
        replayPending = false;
        Screen queued = deferred;
        deferred = null;
        if (queued == null) return;
        Minecraft mc = Minecraft.getInstance();
        // Don't shove it in front of whatever the player opened in the meantime.
        if (mc.level == null || mc.gui.screen() != null) return;
        mc.gui.setScreen(queued);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // A held screen belongs to the connection it was built for.
        deferred = null;
        replayPending = false;
    }

    /** Only the species-choice UIs. NOT DragonEditorScreen (the appearance editor). */
    private static boolean isSpeciesScreen(@Nullable Screen screen) {
        if (screen == null) return false;
        String name = screen.getClass().getSimpleName();
        return name.equals("DragonAltarScreen") || name.equals("DragonSpeciesScreen");
    }
}
