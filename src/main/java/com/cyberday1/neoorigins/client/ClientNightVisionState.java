package com.cyberday1.neoorigins.client;

/**
 * Client-side mirror of the local player's night-vision master switch, pushed by
 * {@code SyncNightVisionPayload}.
 *
 * <p>The server owns the flag; this is a read-only cache for the one consumer
 * that has nothing server-side to read — {@code LightTextureMixin}'s
 * {@code enhanced_vision} brightness boost, which is computed entirely on the
 * client from a capability tag. The {@code minecraft:night_vision} status effect
 * path needs nothing from here, because the server simply stops applying the
 * effect and vanilla syncs its removal.
 *
 * <p>Defaults to {@code true} and is reset to {@code true} on disconnect, so the
 * window before the first sync packet lands shows the always-on behaviour rather
 * than a flicker of darkness. Not valid on a dedicated server.
 */
public final class ClientNightVisionState {

    private static volatile boolean enabled = true;

    private ClientNightVisionState() {}

    public static boolean isEnabled() { return enabled; }

    public static void set(boolean value) { enabled = value; }

    /** Reset to the default-on state when leaving a world. */
    public static void clear() { enabled = true; }
}
