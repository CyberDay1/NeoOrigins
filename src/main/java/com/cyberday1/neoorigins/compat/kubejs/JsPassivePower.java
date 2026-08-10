package com.cyberday1.neoorigins.compat.kubejs;

import net.minecraft.server.level.ServerPlayer;

/**
 * Java-side surface for a JS-defined passive power. Rhino auto-adapts a
 * JS object literal to this interface when it's passed to
 * {@code NeoOrigins.registerPower(id, handler)} — any hook the JS object
 * doesn't supply falls back to the default no-op here.
 *
 * <p>This interface has no KubeJS imports so it's safe to reference from
 * always-loaded code; the actual JS values are only created when KubeJS
 * is present and scripts run.
 */
public interface JsPassivePower {

    /** Called when the JSON power referencing this handler is granted to a player. */
    default void onGranted(ServerPlayer player) {}

    /** Called when the power is revoked from a player. */
    default void onRevoked(ServerPlayer player) {}

    /** Called every server tick while the player has the power. Default: no-op. */
    default void onTick(ServerPlayer player) {}
}
