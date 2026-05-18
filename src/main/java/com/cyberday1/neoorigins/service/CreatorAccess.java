package com.cyberday1.neoorigins.service;

import net.minecraft.server.level.ServerPlayer;

/**
 * Server-authoritative gate for the 2.1 in-game creator. The client UI is
 * gated too, but this is the truth: it guards opening the creator <em>and</em>
 * every write/apply payload, because the creator mutates shared world
 * datapacks.
 *
 * <p>Permission level ≥2 (the established command idiom in this codebase) OR
 * creative mode (covers singleplayer/builders without an explicit op level).
 */
public final class CreatorAccess {

    /** Vanilla op permission level the creator requires. */
    public static final int LEVEL = 2;

    private CreatorAccess() {}

    public static boolean canUse(ServerPlayer sp) {
        return sp.hasPermissions(LEVEL) || sp.isCreative();
    }
}
