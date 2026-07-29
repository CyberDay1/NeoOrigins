package com.cyberday1.neoorigins.power.morph;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server's own record of each player's resolved morph, mirroring
 * {@code ClientMorphState} on the other side of the connection.
 *
 * <p>Until sounds, the server never needed to remember a morph: it resolved one
 * on every active-powers sync, broadcast it and forgot it. Sounds changed that,
 * because {@code Player.getHurtSound} and friends run on <em>both</em> logical
 * sides and the server side has no packet to read the answer out of. Re-walking
 * the power tree on every point of damage would work but would do a lot of work
 * inside a hot path for a cosmetic feature.
 *
 * <p>Written from the single place that already resolves and broadcasts morph
 * state, so it can't drift out of step with what the clients were told. Keyed by
 * UUID rather than entity id because a player's entity id changes across a
 * dimension change and a respawn, and this outlives both.
 */
public final class ServerMorphState {

    private static final Map<UUID, MorphSpec> MORPHS = new ConcurrentHashMap<>();

    private ServerMorphState() {}

    /** Record (or clear, when {@code spec} is null) the morph for a player. */
    public static void set(UUID player, @Nullable MorphSpec spec) {
        if (spec == null) {
            MORPHS.remove(player);
        } else {
            MORPHS.put(player, spec);
        }
    }

    /** The player's resolved morph, or null if they aren't morphed. */
    @Nullable
    public static MorphSpec get(UUID player) {
        return MORPHS.get(player);
    }

    /** Drop a player's morph when they leave. */
    public static void remove(UUID player) {
        MORPHS.remove(player);
    }
}
