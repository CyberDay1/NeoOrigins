package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;

import net.minecraft.server.level.ServerPlayer;

/**
 * Routing layer for {@code neoorigins:resource} powers that declare an external
 * {@code backing} (today only {@code "irons_spellbooks:mana"}).
 *
 * <p>A backed resource does NOT use the internal
 * {@link CompatAttachments.ResourceState} attachment for its value — the backing
 * pool is authoritative. This helper is consulted by every read/write path
 * (getValue, deduct, the {@code resource} condition,
 * {@code change_resource}/{@code set_resource}, and the HUD sync) so the
 * branching lives in exactly one place.
 *
 * <p>Semantics for the Iron's Spells mana backing:
 * <ul>
 *   <li><b>read</b> → {@code (int) MagicData.getMana(player)}.</li>
 *   <li><b>add-delta</b> (grant / regen / spend) → {@code addMana(delta)}; the
 *       mana pool stays authoritative, we never overwrite it absolutely.</li>
 *   <li><b>absolute set</b> ({@code set_resource}, {@code operation:"set"}) → not
 *       meaningful on an additive pool; logged once and NO-OP.</li>
 * </ul>
 *
 * <p>All Iron's-typed symbols live in
 * {@link com.cyberday1.neoorigins.compat.irons_spellbooks.IronsSpellsBridge},
 * reached here by FQN only inside the {@code ModList.isLoaded("irons_spellbooks")}
 * gate — so a pack that declares a mana backing on a server without Iron's Spells
 * degrades to logged no-ops (reads return the fallback, writes do nothing) rather
 * than a class-load error. It does NOT fall back to internal ResourceState.
 */
public final class ResourceBackingRouter {

    private ResourceBackingRouter() {}

    // Warn-once sets keyed by resource id, so a per-tick backed resource logs at
    // most one line per key per cause instead of spamming every tick.
    private static final java.util.Set<String> WARNED_NO_IRONS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<String> WARNED_ABSOLUTE_SET = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static boolean ironsLoaded() {
        return net.neoforged.fml.ModList.get().isLoaded("irons_spellbooks");
    }

    /** True when this key is backed by an external pool (any recognised backing). */
    public static boolean isBacked(String key) {
        return CompatAttachments.isManaBacked(key);
    }

    /**
     * Read the current value of a backed resource. Returns {@code fallback} (use
     * the bar's {@code min}, or 0) when Iron's is absent or the backing is
     * unrecognised — the bar simply reads empty rather than crashing or leaking
     * the internal store.
     */
    public static int read(ServerPlayer player, String key, int fallback) {
        if (CompatAttachments.isManaBacked(key)) {
            if (!ironsLoaded()) {
                warnNoIrons(key);
                return fallback;
            }
            return (int) com.cyberday1.neoorigins.compat.irons_spellbooks.IronsSpellsBridge.getMana(player);
        }
        return fallback;
    }

    /**
     * The LIVE maximum value of a backed resource. For {@code irons_spellbooks:mana}
     * this is the player's dynamic max-mana attribute (moves with gear / level /
     * effects), read the same way Iron's own mana bar reads it. Returns
     * {@code fallback} (the author-declared max) when the key is not backed, when
     * Iron's is absent, or on any read failure — so the bar still has a sane
     * denominator rather than dividing by a stale/zero value.
     */
    public static int maxValue(ServerPlayer player, String key, int fallback) {
        if (!CompatAttachments.isManaBacked(key)) return fallback;
        if (!ironsLoaded()) {
            warnNoIrons(key);
            return fallback;
        }
        int live = (int) com.cyberday1.neoorigins.compat.irons_spellbooks.IronsSpellsBridge.getMaxMana(player);
        // Never hand the HUD a zero/negative denominator (broken Iron's state);
        // fall back to the author max in that case.
        return live > 0 ? live : fallback;
    }

    /**
     * Apply an additive delta to a backed resource (negative to spend/drain).
     * No-op (with a one-time warn) when Iron's is absent. Returns true when a
     * write was routed to the backing (so the caller skips the internal store),
     * false when the key is not backed.
     */
    public static boolean add(ServerPlayer player, String key, int delta) {
        if (!CompatAttachments.isManaBacked(key)) return false;
        if (!ironsLoaded()) {
            warnNoIrons(key);
            return true; // still "handled" — do NOT fall back to ResourceState
        }
        com.cyberday1.neoorigins.compat.irons_spellbooks.IronsSpellsBridge.addMana(player, (float) delta);
        return true;
    }

    /**
     * Handle an absolute {@code set} against a backed resource: not meaningful on
     * an additive pool, so log once and no-op. Returns true when the key is
     * backed (caller must skip its own set), false otherwise.
     */
    public static boolean handleAbsoluteSet(String key) {
        if (!CompatAttachments.isManaBacked(key)) return false;
        if (WARNED_ABSOLUTE_SET.add(key)) {
            NeoOrigins.LOGGER.warn(
                "[Iron's Spells] resource '{}' is backed by irons_spellbooks:mana, which is additive-only — an absolute set (set_resource / operation:\"set\") is ignored. Use change_resource with a +/- amount instead.",
                key);
        }
        return true;
    }

    private static void warnNoIrons(String key) {
        if (WARNED_NO_IRONS.add(key)) {
            NeoOrigins.LOGGER.warn(
                "[Iron's Spells] resource '{}' declares backing \"irons_spellbooks:mana\" but Iron's Spells 'n Spellbooks (irons_spellbooks) isn't installed — its bar reads empty and writes do nothing. Gate the origin with \"required_mods\": [\"irons_spellbooks\"] to hide it entirely.",
                key);
        }
    }

    /** Clear warn-once state (called on datapack reload alongside the meta caches). */
    public static void clearWarnings() {
        WARNED_NO_IRONS.clear();
        WARNED_ABSOLUTE_SET.clear();
    }
}
