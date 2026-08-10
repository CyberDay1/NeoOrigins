package com.cyberday1.neoorigins.compat.kubejs;

import net.minecraft.server.level.ServerPlayer;

/**
 * Java-side surface for a JS-defined active (keybind) power. Rhino
 * auto-adapts a JS object literal to this interface when it's passed to
 * {@code NeoOrigins.registerActivePower(id, handler)}.
 *
 * <p>{@link #onUse} is the only required method; the optional grant /
 * revoke hooks have default no-ops so JS objects can omit them.
 *
 * <p>{@code onUse} returns a {@code boolean} that mirrors
 * {@code AbstractActivePower.execute}'s contract: {@code true} means the
 * power fired and the cooldown should be consumed; {@code false} means
 * nothing happened (no cooldown, no hunger cost).
 */
public interface JsActivePower {

    /**
     * Called when the player presses the keybind for this power.
     * @return {@code true} to consume the cooldown / hunger cost,
     *         {@code false} to skip (e.g., no valid target)
     */
    boolean onUse(ServerPlayer player);

    /** Optional — called when the power is granted to a player. */
    default void onGranted(ServerPlayer player) {}

    /** Optional — called when the power is revoked from a player. */
    default void onRevoked(ServerPlayer player) {}
}
