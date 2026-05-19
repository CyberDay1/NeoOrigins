package com.cyberday1.neoorigins.service;

import net.minecraft.server.level.ServerPlayer;

/**
 * Server-authoritative gate for the 2.1 in-game creator. The client UI is
 * gated too, but this is the truth: it guards opening the creator <em>and</em>
 * every write/apply payload, because the creator mutates shared world
 * datapacks.
 *
 * <p>Permission level ≥2 (the established command idiom in this codebase) OR
 * creative mode — but creative is honored ONLY on an integrated
 * (singleplayer / LAN-host) server. Creative is a gameplay state, not an
 * authorization level: on a dedicated server any player may be creative
 * (build/minigame servers, other plugins' {@code /gamemode}), so honoring it
 * there would hand the shared-datapack creator to non-admins. Singleplayer /
 * LAN keeps the "builder without an explicit op level" convenience.
 */
public final class CreatorAccess {

    /** Vanilla op permission level the creator requires. */
    public static final int LEVEL = 2;

    private CreatorAccess() {}

    public static boolean canUse(ServerPlayer sp) {
        if (sp.hasPermissions(LEVEL)) return true;
        var server = sp.level().getServer();
        return sp.isCreative() && server != null && server.isSingleplayer();
    }
}
