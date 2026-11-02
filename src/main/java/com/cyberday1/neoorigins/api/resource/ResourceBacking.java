package com.cyberday1.neoorigins.api.resource;

import net.minecraft.server.level.ServerPlayer;

/**
 * An external pool that a {@code neoorigins:resource} bar can read and write instead of
 * NeoOrigins' own per-player store. A bar opts in with {@code "backing": "<id>"}, where the
 * id is the one passed to
 * {@link com.cyberday1.neoorigins.api.NeoOriginsAPI#registerResourceBacking}.
 *
 * <p>The pool stays authoritative: NeoOrigins never stores a value for a backed bar, it
 * reads this backing on every check and routes every change through it. A backed bar runs
 * from 0 to {@link #max}. All methods are called on the server thread.
 */
public interface ResourceBacking {

    /**
     * Whether the pool can be used now, e.g. whether the mod that owns it is loaded. While
     * false the bar reads as its fallback and writes do nothing, with one warning per bar.
     */
    default boolean available() {
        return true;
    }

    /** The player's current value. */
    int read(ServerPlayer player);

    /** Adds {@code delta} to the pool; negative to spend. The pool applies its own limits. */
    void add(ServerPlayer player, int delta);

    /** The player's live maximum. Zero or less means "use the bar's declared {@code max}". */
    default int max(ServerPlayer player) {
        return 0;
    }

    /**
     * Sets the pool to an absolute value, for {@code set_resource} and
     * {@code change_resource} with {@code "operation": "set"}. Return false if the pool
     * only takes additive changes; the set is then ignored with one warning per bar.
     */
    default boolean set(ServerPlayer player, int value) {
        return false;
    }
}
