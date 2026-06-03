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
import net.minecraft.resources.ResourceLocation;
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

    private static final ResourceLocation CLASS_LAYER =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "class");

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
        int evolutionTier = data.getEvolutionTier();
        java.util.HashSet<ResourceLocation> seen = new java.util.HashSet<>();
        for (var entry : data.getOrigins().entrySet()) {
            boolean isClassLayer = CLASS_LAYER.equals(entry.getKey());
            Origin origin = OriginDataManager.INSTANCE.getOrigin(entry.getValue());
            if (origin == null) continue;
            // Apply evolution tier overlays — classes don't evolve, only origins
            List<ResourceLocation> effectivePowers = isClassLayer
                ? origin.powers()
                : origin.powersForTier(evolutionTier);
            for (ResourceLocation powerId : effectivePowers) {
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
        for (ResourceLocation powerId : data.getDynamicGrantedPowers()) {
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

    /**
     * Like {@link #forEachOfType}, but skips holders whose type extends
     * {@link com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower}
     * and is currently toggled off for this player.
     *
     * <p>Event handlers (e.g. spawn cancellation, hit modifiers) reading
     * toggleable powers must use this variant — only {@code onTick} honors the
     * toggle automatically; everywhere else has to gate itself. Without this,
     * the player toggles "off", the keybind says "Power disabled", but the
     * event handler keeps applying the effect.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <C extends PowerConfiguration, T extends PowerType<C>>
    void forEachOfTypeActive(ServerPlayer player, Class<T> typeClass, Consumer<C> action) {
        for (PowerHolder<?> holder : getOrBuild(player).allPowers) {
            if (!typeClass.isInstance(holder.type())) continue;
            if (holder.type() instanceof com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower<?>
                    && ((com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower) holder.type())
                            .isToggledOff(player, holder.config())) {
                continue;
            }
            action.accept((C) holder.config());
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
            if (!holder.isConditionSatisfied(player)) continue;
            if (holder.type() instanceof com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower<?>
                    && ((com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower) holder.type())
                            .isToggledOff(player, holder.config())) {
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
        int tier = data.getEvolutionTier();
        java.util.HashSet<ResourceLocation> revoked = new java.util.HashSet<>();
        for (var entry : data.getOrigins().entrySet()) {
            Origin origin = OriginDataManager.INSTANCE.getOrigin(entry.getValue());
            if (origin == null) continue;
            boolean isClassLayer = CLASS_LAYER.equals(entry.getKey());
            List<ResourceLocation> effectivePowers = isClassLayer
                ? origin.powers() : origin.powersForTier(tier);
            for (ResourceLocation powerId : effectivePowers) {
                if (!revoked.add(powerId)) continue;
                PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                if (holder != null) holder.onRevoked(player);
            }
        }
        // Also revoke dynamic grants.
        for (ResourceLocation powerId : new java.util.ArrayList<>(data.getDynamicGrantedPowers())) {
            if (!revoked.add(powerId)) {
                data.removeDynamicGrant(powerId);
                continue;
            }
            PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
            if (holder != null) holder.onRevoked(player);
            data.removeDynamicGrant(powerId);
        }
        // Clear the global-power ledger too — the dynamic grants backing it were
        // just torn down above, so the ledger must be emptied or reconcilePlayer
        // (run on the next login / datapack-sync) would see the powers as still
        // owned and never re-grant them.
        for (ResourceLocation powerId : new java.util.ArrayList<>(data.getGlobalGrantedPowers())) {
            data.removeGlobalGrant(powerId);
        }

        // Final sweep: remove any neoorigins:power_* attribute modifiers still
        // attached to the player. The per-power onRevoked above handles the
        // happy path, but two cases leak otherwise:
        //   1. Legacy-format modifier IDs in NBT from older versions.
        //   2. Modifiers from origins whose JSON is no longer loaded (OriginDataManager
        //      returned null at line `if (origin == null) continue;` above).
        // Cheap to run; in the common case, all targeted modifiers were already
        // removed by the loop above and this is a no-op.
        com.cyberday1.neoorigins.power.builtin.AttributeModifierPower.purgeAllOriginModifiers(player);
        // Belt-and-suspenders: clear ALL event handlers for this player.
        // Covers the same class of leaks as purgeAllOriginModifiers — if an
        // origin's JSON was removed (null above) or a Config record equality
        // mismatch prevented token-based unregistration, this ensures stale
        // food_restriction / action_on_event handlers don't persist.
        com.cyberday1.neoorigins.service.EventPowerIndex.clearAll(player.getUUID());
        com.cyberday1.neoorigins.power.builtin.ActionOnEventPower.clearTokens(player.getUUID());
        // Clamp health to the new max now that all attribute modifiers are gone.
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /**
     * Collects every power id currently active on the player across ALL layers
     * (tier-aware for non-class layers) plus dynamic grants. The layer being
     * mutated is overridden with {@code overrideOrigin} ({@code null} drops it),
     * so the result reflects the post-change state regardless of whether the
     * caller has already written it to the attachment. Used to scope the
     * attribute-modifier orphan sweep so other layers' modifiers survive.
     */
    private static java.util.Set<ResourceLocation> collectActivePowerIds(
            ServerPlayer player, ResourceLocation overrideLayer, ResourceLocation overrideOrigin) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        int tier = data.getEvolutionTier();
        java.util.Map<ResourceLocation, ResourceLocation> origins =
            new java.util.HashMap<>(data.getOrigins());
        if (overrideLayer != null) {
            if (overrideOrigin == null) origins.remove(overrideLayer);
            else origins.put(overrideLayer, overrideOrigin);
        }
        java.util.Set<ResourceLocation> ids = new java.util.HashSet<>();
        for (var entry : origins.entrySet()) {
            Origin origin = OriginDataManager.INSTANCE.getOrigin(entry.getValue());
            if (origin == null) continue;
            boolean isClassLayer = CLASS_LAYER.equals(entry.getKey());
            List<ResourceLocation> effectivePowers = isClassLayer
                ? origin.powers() : origin.powersForTier(tier);
            ids.addAll(effectivePowers);
        }
        ids.addAll(data.getDynamicGrantedPowers());
        return ids;
    }

    /**
     * Revokes powers from oldOrigin and grants powers from newOrigin.
     * Posts PowerRevokedEvent / PowerGrantedEvent for each power changed.
     * Grant/revoke bypasses dimension restrictions to ensure clean state transitions.
     */
    public static void applyOriginPowers(ServerPlayer player, ResourceLocation layerId,
                                          ResourceLocation oldOriginId, ResourceLocation newOriginId) {
        if (oldOriginId != null) {
            Origin oldOrigin = OriginDataManager.INSTANCE.getOrigin(oldOriginId);
            if (oldOrigin != null) {
                for (ResourceLocation powerId : oldOrigin.powers()) {
                    PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                    if (holder != null) {
                        holder.onRevoked(player);
                        NeoForge.EVENT_BUS.post(new PowerRevokedEvent(player, powerId));
                        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
                            player, com.cyberday1.neoorigins.service.EventPowerIndex.Event.LOST, powerId);
                        com.cyberday1.neoorigins.compat.kubejs.KubeJSEventBridge.firePowerRevoked(player, powerId);
                    }
                }
            }
            // Sweep any orphaned neoorigins attribute modifiers from the old origin
            // in case the JSON was edited or a power was removed since it was granted.
            // Layer-aware: only purge modifiers whose power is no longer active across
            // ANY layer. A blanket wipe here would drop the OTHER layers' attribute
            // boosts (e.g. changing the class layer would erase the origin layer's
            // +HP), since this method only re-grants `newOriginId`'s own powers below.
            java.util.Set<ResourceLocation> activePowers =
                collectActivePowerIds(player, layerId, newOriginId);
            com.cyberday1.neoorigins.power.builtin.AttributeModifierPower
                .purgeOriginModifiersExcept(player, activePowers);
        }
        Origin newOrigin = OriginDataManager.INSTANCE.getOrigin(newOriginId);
        if (newOrigin != null) {
            for (ResourceLocation powerId : newOrigin.powers()) {
                PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                if (holder != null) {
                    holder.onGranted(player);
                    NeoForge.EVENT_BUS.post(new PowerGrantedEvent(player, powerId));
                    com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
                        player, com.cyberday1.neoorigins.service.EventPowerIndex.Event.GAINED, powerId);
                    com.cyberday1.neoorigins.compat.kubejs.KubeJSEventBridge.firePowerGranted(player, powerId);
                }
            }
        }
        // Clamp health to the new max — attribute modifiers may have changed
        // max_health (e.g. swapping from a +HP origin to one without).
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        // KubeJS: centralized origin_changed fire — covers user picks, admin
        // /set, /reset, and cascade invalidation. Fires whenever the layer's
        // origin id transitions, including null↔value (first-pick / clear).
        if (!java.util.Objects.equals(oldOriginId, newOriginId)) {
            com.cyberday1.neoorigins.compat.kubejs.KubeJSEventBridge.fireOriginChanged(
                player, layerId, oldOriginId, newOriginId);
        }
    }
}
