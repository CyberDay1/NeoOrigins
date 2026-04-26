package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.power.builtin.TogglePower;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Single entry point for reading and writing per-player toggle states.
 *
 * <p>Toggle state is stored in {@link CompatAttachments#toggleState()}
 * keyed by power id (e.g. {@code "mypack:my_toggle"}). The map is sparse —
 * powers that have never been flipped don't have an entry. {@link #isOn}
 * resolves the configured {@code default} from the registered
 * {@link TogglePower} when no entry exists, so JSON-declared
 * {@code "default": true} actually means "on until flipped off."
 *
 * <p>Pack authors don't see this class directly — they use:
 * <ul>
 *   <li>{@code origins:power_active { power: "..." }} — read</li>
 *   <li>{@code neoorigins:toggle { power: "...", value?: bool }} — write</li>
 * </ul>
 * Internal callers (mixins, custom power types) can use this facade instead
 * of going through the attachment manually.
 */
public final class Toggles {

    private Toggles() {}

    /** Current value, falling back to the registered TogglePower's {@code default} if unset. */
    public static boolean isOn(Player player, String powerId) {
        boolean def = resolveDefault(powerId);
        return player.getData(CompatAttachments.toggleState()).isActive(powerId, def);
    }

    /** Force the toggle to a specific value. */
    public static void setOn(Player player, String powerId, boolean value) {
        player.getData(CompatAttachments.toggleState()).set(powerId, value);
    }

    /** Flip the current value (resolving the default first if no entry exists) and return the new value. */
    public static boolean flip(Player player, String powerId) {
        boolean cur = isOn(player, powerId);
        setOn(player, powerId, !cur);
        return !cur;
    }

    /**
     * Resolve the configured default by looking up the registered power.
     * Falls back to {@code false} for non-toggle powers or unknown ids.
     */
    private static boolean resolveDefault(String powerId) {
        if (powerId == null || powerId.isBlank()) return false;
        ResourceLocation id;
        try {
            id = ResourceLocation.parse(powerId);
        } catch (Exception e) {
            return false;
        }
        var holder = PowerDataManager.INSTANCE.getPower(id);
        if (holder == null) return false;
        if (holder.type() instanceof TogglePower
            && holder.config() instanceof TogglePower.Config cfg) {
            return cfg.defaultValue();
        }
        return false;
    }
}
