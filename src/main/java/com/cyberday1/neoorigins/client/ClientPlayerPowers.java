package com.cyberday1.neoorigins.client;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side, per-player mirror of every visible player's NeoOrigins state:
 * chosen origins (layer → origin id), granted powers + toggle state, and the
 * union of active capability tags. Unlike {@link ClientActivePowers} (which
 * mirrors only the LOCAL player and drives client-predicted movement), this
 * holds the state of ALL players the client can see, so any observer can read
 * any visible player's state.
 *
 * <p>Populated by {@code SyncPlayerPowersPayload}, which the server broadcasts to
 * every client tracking a player (and the player themselves) on join, respawn,
 * origin change, toggle flip, dimension change, and start-tracking. Keyed by the
 * player's {@link UUID} — stable across the entity-id churn a dimension change
 * causes, and exactly the key a Figura {@code Avatar} exposes as its owner, so
 * the Figura soft-dep API can answer a query for an arbitrary avatar with no
 * UUID→entity resolution. Entries are evicted on stop-tracking / logout, and the
 * whole map is cleared on disconnect, so a stale entry from a prior session can
 * never leak into a new one.
 *
 * <p>Not valid on a dedicated server — only populated on the logical client.
 */
public final class ClientPlayerPowers {

    /** Immutable per-player snapshot. Empty defaults keep queries null-safe. */
    public record Entry(
        Map<ResourceLocation, ResourceLocation> origins,
        Map<ResourceLocation, Boolean> powers,
        Set<String> capabilities,
        int evolutionTier
    ) {
        static final Entry EMPTY = new Entry(Map.of(), Map.of(), Set.of(), 0);
    }

    private static final Map<UUID, Entry> BY_PLAYER = new ConcurrentHashMap<>();

    private ClientPlayerPowers() {}

    /** Replace the stored snapshot for a player (defensively copied). */
    public static void set(UUID playerId,
                           Map<ResourceLocation, ResourceLocation> origins,
                           Map<ResourceLocation, Boolean> powers,
                           Set<String> capabilities,
                           int evolutionTier) {
        BY_PLAYER.put(playerId, new Entry(
            Map.copyOf(origins), Map.copyOf(powers), Set.copyOf(capabilities), evolutionTier));
    }

    /** Drop the snapshot for a player (stop-tracking / logout). */
    public static void remove(UUID playerId) {
        BY_PLAYER.remove(playerId);
    }

    public static void clear() {
        BY_PLAYER.clear();
    }

    /** True if the client currently holds any state for {@code playerId}. */
    public static boolean isTracked(UUID playerId) {
        return BY_PLAYER.containsKey(playerId);
    }

    /**
     * The player's origin id on the primary {@code neoorigins:origin} layer, or
     * {@code null} if they have no origin there (or aren't tracked). This is the
     * single-value convenience the Figura {@code getOrigin()} method returns.
     */
    public static ResourceLocation primaryOrigin(UUID playerId) {
        Entry e = BY_PLAYER.get(playerId);
        if (e == null) return null;
        ResourceLocation origin = e.origins().get(
            ResourceLocation.fromNamespaceAndPath("neoorigins", "origin"));
        if (origin != null) return origin;
        // No canonical origin layer — fall back to the first origin present so a
        // pack that only defines a class layer still answers something useful.
        return e.origins().values().stream().findFirst().orElse(null);
    }

    /** All of the player's origin ids across every layer (may be empty). */
    public static List<ResourceLocation> origins(UUID playerId) {
        Entry e = BY_PLAYER.get(playerId);
        if (e == null) return List.of();
        return List.copyOf(e.origins().values());
    }

    /** layer id → origin id map for the player (may be empty). */
    public static Map<ResourceLocation, ResourceLocation> originsByLayer(UUID playerId) {
        Entry e = BY_PLAYER.get(playerId);
        return e == null ? Map.of() : e.origins();
    }

    /** True if the player has {@code powerId} granted, regardless of toggle state. */
    public static boolean hasPower(UUID playerId, ResourceLocation powerId) {
        return BY_PLAYER.getOrDefault(playerId, Entry.EMPTY).powers().containsKey(powerId);
    }

    /**
     * True if the player has {@code powerId} granted AND it's active (non-toggleable
     * or toggled on). Mirrors {@link ClientActivePowers#isActive} but for any player.
     */
    public static boolean isPowerActive(UUID playerId, ResourceLocation powerId) {
        return Boolean.TRUE.equals(
            BY_PLAYER.getOrDefault(playerId, Entry.EMPTY).powers().get(powerId));
    }

    /** All power ids granted to the player, regardless of toggle state (may be empty). */
    public static List<ResourceLocation> powers(UUID playerId) {
        return List.copyOf(BY_PLAYER.getOrDefault(playerId, Entry.EMPTY).powers().keySet());
    }

    /** True if any currently-active power on the player grants {@code tag}. */
    public static boolean hasCapability(UUID playerId, String tag) {
        return BY_PLAYER.getOrDefault(playerId, Entry.EMPTY).capabilities().contains(tag);
    }

    /** All active capability tags on the player (may be empty). */
    public static Set<String> capabilities(UUID playerId) {
        return BY_PLAYER.getOrDefault(playerId, Entry.EMPTY).capabilities();
    }

    /**
     * The player's current evolution tier (0 when unknown / untracked). Synced
     * from the server per player so tier-reactive Figura models resolve the same
     * on every observer's client, not only the player's own.
     */
    public static int tier(UUID playerId) {
        return BY_PLAYER.getOrDefault(playerId, Entry.EMPTY).evolutionTier();
    }
}
