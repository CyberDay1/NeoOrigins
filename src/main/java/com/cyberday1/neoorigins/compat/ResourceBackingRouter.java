package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.resource.ResourceBacking;
import com.cyberday1.neoorigins.compat.irons_spellbooks.IronsManaBacking;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routing layer for {@code neoorigins:resource} powers that declare an external
 * {@code backing}, and the registry of backings ({@code irons_spellbooks:mana} built in,
 * others added through {@link com.cyberday1.neoorigins.api.NeoOriginsAPI#registerResourceBacking}).
 *
 * <p>A backed resource does NOT use the internal {@link CompatAttachments.ResourceState}
 * attachment for its value: the backing pool is authoritative. Every read/write path
 * (getValue, deduct, the {@code resource} condition, {@code change_resource}/{@code set_resource},
 * and the HUD sync) goes through here so the branching lives in one place.
 *
 * <p>A bar naming an unregistered backing is treated as internally stored, with one warning.
 * A registered backing that is unavailable, or that throws, makes reads return the fallback
 * and writes no-op, with one warning per bar; it never falls back to the internal store.
 */
public final class ResourceBackingRouter {

    private ResourceBackingRouter() {}

    private static final Map<String, ResourceBacking> BACKINGS = new ConcurrentHashMap<>();

    static {
        BACKINGS.put(CompatAttachments.BACKING_IRONS_MANA, new IronsManaBacking());
    }

    // Warn-once sets keyed by resource id, so a per-tick backed resource logs at
    // most one line per key per cause instead of spamming every tick.
    private static final Set<String> WARNED_UNKNOWN = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED_UNAVAILABLE = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED_ABSOLUTE_SET = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED_FAILED = ConcurrentHashMap.newKeySet();

    /** Registers a backing under {@code id}. Throws if the id is already taken. */
    public static void register(String id, ResourceBacking backing) {
        if (BACKINGS.putIfAbsent(id, java.util.Objects.requireNonNull(backing, "backing")) != null) {
            throw new IllegalStateException("A resource backing is already registered as '" + id + "'");
        }
    }

    /** The backing registered as {@code id}, or null. */
    public static ResourceBacking backing(String id) {
        return id == null ? null : BACKINGS.get(id);
    }

    /** The registered backing a resource key declares, or null when it is internally stored. */
    private static ResourceBacking backingFor(String key) {
        String id = CompatAttachments.resourceBacking(key);
        if (id == null) return null;
        ResourceBacking b = BACKINGS.get(id);
        if (b == null && WARNED_UNKNOWN.add(key)) {
            NeoOrigins.LOGGER.warn(
                "resource '{}' declares backing \"{}\", which no installed mod registers. The bar uses NeoOrigins' own store instead.",
                key, id);
        }
        return b;
    }

    /** True when this key is backed by a registered external pool. */
    public static boolean isBacked(String key) {
        return backingFor(key) != null;
    }

    /** The current value of a backed resource; {@code fallback} when it is not backed or unusable. */
    public static int read(ServerPlayer player, String key, int fallback) {
        ResourceBacking b = usable(key);
        if (b == null) return fallback;
        try {
            return b.read(player);
        } catch (RuntimeException e) {
            warnFailed(key, e);
            return fallback;
        }
    }

    /**
     * The LIVE maximum of a backed resource (for Iron's mana, the player's max-mana attribute).
     * Returns {@code fallback} (the declared max) when the key is not backed, the backing is
     * unusable, or it reports zero or less, so the HUD never divides by a stale/zero value.
     */
    public static int maxValue(ServerPlayer player, String key, int fallback) {
        ResourceBacking b = usable(key);
        if (b == null) return fallback;
        try {
            int live = b.max(player);
            return live > 0 ? live : fallback;
        } catch (RuntimeException e) {
            warnFailed(key, e);
            return fallback;
        }
    }

    /**
     * Applies an additive delta to a backed resource (negative to spend). Returns true when
     * the key is backed, so the caller skips the internal store, even if the write was dropped.
     */
    public static boolean add(ServerPlayer player, String key, int delta) {
        if (!isBacked(key)) return false;
        ResourceBacking b = usable(key);
        if (b == null) return true;
        try {
            b.add(player, delta);
        } catch (RuntimeException e) {
            warnFailed(key, e);
        }
        return true;
    }

    /**
     * Handles an absolute set against a backed resource. Returns true when the key is backed
     * (the caller must skip its own set); a backing that refuses absolute sets is warned once.
     */
    public static boolean handleAbsoluteSet(ServerPlayer player, String key, int value) {
        if (!isBacked(key)) return false;
        ResourceBacking b = usable(key);
        if (b == null) return true;
        try {
            if (!b.set(player, value) && WARNED_ABSOLUTE_SET.add(key)) {
                NeoOrigins.LOGGER.warn(
                    "resource '{}' is backed by \"{}\", which only takes additive changes; an absolute set (set_resource / operation:\"set\") is ignored. Use change_resource with a +/- amount instead.",
                    key, CompatAttachments.resourceBacking(key));
            }
        } catch (RuntimeException e) {
            warnFailed(key, e);
        }
        return true;
    }

    /** The key's backing if it is registered and available, else null (warning once when unavailable). */
    private static ResourceBacking usable(String key) {
        ResourceBacking b = backingFor(key);
        if (b == null) return null;
        boolean available;
        try {
            available = b.available();
        } catch (RuntimeException e) {
            warnFailed(key, e);
            return null;
        }
        if (!available && WARNED_UNAVAILABLE.add(key)) {
            NeoOrigins.LOGGER.warn(
                "resource '{}' declares backing \"{}\", which is not available (is its mod installed?). Its bar reads empty and writes do nothing. Gate the origin with \"required_mods\" to hide it entirely.",
                key, CompatAttachments.resourceBacking(key));
        }
        return available ? b : null;
    }

    private static void warnFailed(String key, RuntimeException e) {
        if (WARNED_FAILED.add(key)) {
            NeoOrigins.LOGGER.error("resource '{}': backing \"{}\" threw; the bar reads empty and writes do nothing",
                key, CompatAttachments.resourceBacking(key), e);
        }
    }

    /** Clear warn-once state (called on datapack reload alongside the meta caches). */
    public static void clearWarnings() {
        WARNED_UNKNOWN.clear();
        WARNED_UNAVAILABLE.clear();
        WARNED_ABSOLUTE_SET.clear();
        WARNED_FAILED.clear();
    }
}
