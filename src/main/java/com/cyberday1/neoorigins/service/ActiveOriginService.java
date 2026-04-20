package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOriginsConfig;
import com.cyberday1.neoorigins.api.event.PowerGrantedEvent;
import com.cyberday1.neoorigins.api.event.PowerRevokedEvent;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Central service for traversing a player's active powers.
 * All iteration is deterministic: layers are visited in sorted ID order.
 *
 * <p>Read-side lookups ({@link #forEach}, {@link #forEachOfType}, {@link #has},
 * {@link #allPowers}, {@link #activePowers}, {@link #activeClassPowers}) go through
 * a per-player cache keyed by (dimension, player-data version, origin-manager version,
 * power-manager version, dim-restrictions version). The cache is rebuilt on any mismatch
 * and cleared on logout via {@link #invalidate(UUID)}.
 */
public final class ActiveOriginService {

    private ActiveOriginService() {}

    private static final Identifier CLASS_LAYER =
        Identifier.fromNamespaceAndPath("neoorigins", "class");

    // ── Per-player power cache ──────────────────────────────────────────
    private static final Map<UUID, CacheEntry> CACHE = new ConcurrentHashMap<>();

    /** Immutable snapshot of a player's resolved (dimension-filtered) power set. */
    private static final class CacheEntry {
        final ResourceKey<Level> dimension;
        final int dataVersion;
        final int originMgrVersion;
        final int powerMgrVersion;
        final int restrictionsVersion;
        final List<PowerHolder<?>> allPowers;
        final List<PowerHolder<?>> originActive;
        final List<PowerHolder<?>> classActive;

        CacheEntry(ResourceKey<Level> dim, int dv, int omv, int pmv, int rv,
                   List<PowerHolder<?>> all,
                   List<PowerHolder<?>> originActive,
                   List<PowerHolder<?>> classActive) {
            this.dimension = dim;
            this.dataVersion = dv;
            this.originMgrVersion = omv;
            this.powerMgrVersion = pmv;
            this.restrictionsVersion = rv;
            this.allPowers = all;
            this.originActive = originActive;
            this.classActive = classActive;
        }
    }

    private static CacheEntry getOrBuild(ServerPlayer player) {
        ResourceKey<Level> dim = player.level().dimension();
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        int dv = data.version();
        int omv = OriginDataManager.INSTANCE.version();
        int pmv = PowerDataManager.INSTANCE.version();
        int rv = NeoOriginsConfig.restrictionsVersion();

        UUID uuid = player.getUUID();
        CacheEntry cur = CACHE.get(uuid);
        if (cur != null
            && cur.dimension.equals(dim)
            && cur.dataVersion == dv
            && cur.originMgrVersion == omv
            && cur.powerMgrVersion == pmv
            && cur.restrictionsVersion == rv) {
            return cur;
        }

        List<PowerHolder<?>> all = new ArrayList<>();
        List<PowerHolder<?>> originActive = new ArrayList<>();
        List<PowerHolder<?>> classActive = new ArrayList<>();
        java.util.HashSet<Identifier> seen = new java.util.HashSet<>();
        for (var entry : data.getOrigins().entrySet()) {
            boolean isClassLayer = CLASS_LAYER.equals(entry.getKey());
            Origin origin = OriginDataManager.INSTANCE.getOrigin(entry.getValue());
            if (origin == null) continue;
            for (Identifier powerId : origin.powers()) {
                if (NeoOriginsConfig.isPowerRestrictedInDimension(powerId, dim)) continue;
                PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                if (holder == null) continue;
                if (!seen.add(powerId)) continue;
                all.add(holder);
                if (holder.isActive()) {
                    if (isClassLayer) classActive.add(holder);
                    else originActive.add(holder);
                }
            }
        }
        // Dynamic grants from grant_power action — treated as origin-layer powers.
        for (Identifier powerId : data.getDynamicGrantedPowers()) {
            if (NeoOriginsConfig.isPowerRestrictedInDimension(powerId, dim)) continue;
            if (!seen.add(powerId)) continue;
            PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
            if (holder == null) continue;
            all.add(holder);
            if (holder.isActive()) originActive.add(holder);
        }

        CacheEntry fresh = new CacheEntry(dim, dv, omv, pmv, rv,
            List.copyOf(all),
            List.copyOf(originActive),
            List.copyOf(classActive));
        CACHE.put(uuid, fresh);
        return fresh;
    }

    /** Clear a player's cache entry (call on logout / player disposal). */
    public static void invalidate(UUID uuid) {
        CACHE.remove(uuid);
    }

    // ── Public API ──────────────────────────────────────────────────────

    /** Returns all power holders in deterministic (sorted layer → power) order. */
    public static List<PowerHolder<?>> allPowers(ServerPlayer player) {
        return getOrBuild(player).allPowers;
    }

    /** Iterates all power holders in sorted layer order, respecting dimension restrictions. */
    public static void forEach(ServerPlayer player, Consumer<PowerHolder<?>> action) {
        for (PowerHolder<?> holder : getOrBuild(player).allPowers) {
            action.accept(holder);
        }
    }

    /** Iterates powers of a specific type, passing the typed config to the action. */
    @SuppressWarnings("unchecked")
    public static <C extends PowerConfiguration, T extends PowerType<C>>
    void forEachOfType(ServerPlayer player, Class<T> typeClass, Consumer<C> action) {
        for (PowerHolder<?> holder : getOrBuild(player).allPowers) {
            if (typeClass.isInstance(holder.type())) {
                action.accept((C) holder.config());
            }
        }
    }

    /** Returns true if the player has a power of the given type satisfying the predicate. */
    @SuppressWarnings("unchecked")
    public static <C extends PowerConfiguration, T extends PowerType<C>>
    boolean has(ServerPlayer player, Class<T> typeClass, Predicate<C> predicate) {
        for (PowerHolder<?> holder : getOrBuild(player).allPowers) {
            if (typeClass.isInstance(holder.type())
                && predicate.test((C) holder.config())) {
                return true;
            }
        }
        return false;
    }

    /** Returns active (keybind-slot) power holders from origin layers only (excludes class). */
    public static List<PowerHolder<?>> activePowers(ServerPlayer player) {
        return getOrBuild(player).originActive;
    }

    /** Returns active (keybind-slot) power holders from the class layer only. */
    public static List<PowerHolder<?>> activeClassPowers(ServerPlayer player) {
        return getOrBuild(player).classActive;
    }

    /**
     * True if any power currently granted to the player (dimension-filtered,
     * and for toggleable powers, not toggled off) declares the given
     * {@link com.cyberday1.neoorigins.api.power.PowerType#capabilities capability} tag.
     *
     * Server-side counterpart to {@code ClientActivePowers.hasCapability}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean hasCapability(ServerPlayer player, String tag) {
        for (PowerHolder<?> holder : getOrBuild(player).allPowers) {
            if (holder.type() instanceof com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower<?> toggle
                    && toggle.isToggledOff(player)) {
                continue;
            }
            if (((PowerHolder) holder).type().capabilities(holder.config()).contains(tag)) {
                return true;
            }
        }
        return false;
    }

    // ── Mutating operations (bypass dimension restrictions) ─────────────

    /** Revokes all powers across all layers. Called on player reset. */
    public static void revokeAllPowers(ServerPlayer player) {
        // Revoke bypasses dimension restrictions — always clean up all powers.
        // Iterate the raw origin map directly; we don't care about the cache here
        // (and the caller will typically mutate `data` right after, invalidating it).
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        java.util.HashSet<Identifier> revoked = new java.util.HashSet<>();
        for (var entry : data.getOrigins().entrySet()) {
            Origin origin = OriginDataManager.INSTANCE.getOrigin(entry.getValue());
            if (origin == null) continue;
            for (Identifier powerId : origin.powers()) {
                if (!revoked.add(powerId)) continue;
                PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                if (holder != null) holder.onRevoked(player);
            }
        }
        // Also revoke dynamic grants.
        for (Identifier powerId : new java.util.ArrayList<>(data.getDynamicGrantedPowers())) {
            if (!revoked.add(powerId)) {
                data.removeDynamicGrant(powerId);
                continue;
            }
            PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
            if (holder != null) holder.onRevoked(player);
            data.removeDynamicGrant(powerId);
        }
    }

    /**
     * Revokes powers from oldOrigin and grants powers from newOrigin.
     * Posts PowerRevokedEvent / PowerGrantedEvent for each power changed.
     * Grant/revoke bypasses dimension restrictions to ensure clean state transitions.
     */
    public static void applyOriginPowers(ServerPlayer player, Identifier layerId,
                                          Identifier oldOriginId, Identifier newOriginId) {
        if (oldOriginId != null) {
            Origin oldOrigin = OriginDataManager.INSTANCE.getOrigin(oldOriginId);
            if (oldOrigin != null) {
                for (Identifier powerId : oldOrigin.powers()) {
                    PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                    if (holder != null) {
                        holder.onRevoked(player);
                        NeoForge.EVENT_BUS.post(new PowerRevokedEvent(player, powerId));
                    }
                }
            }
        }
        Origin newOrigin = OriginDataManager.INSTANCE.getOrigin(newOriginId);
        if (newOrigin != null) {
            for (Identifier powerId : newOrigin.powers()) {
                PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                if (holder != null) {
                    holder.onGranted(player);
                    NeoForge.EVENT_BUS.post(new PowerGrantedEvent(player, powerId));
                }
            }
        }
    }
}
