package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.config.AdminConfig;
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
 * <p>Every read-side lookup — {@link #forEach}, {@link #forEachOfType},
 * {@link #forEachOfTypeActive}, {@link #has}, {@link #hasCapability},
 * {@link #allPowers}, {@link #activePowers}, {@link #activeClassPowers} — goes
 * through a per-player cache keyed by (dimension, player-data version, origin-manager
 * version, power-manager version, dim-restrictions version). The cache is rebuilt on
 * any mismatch and cleared on logout via {@link #invalidate(UUID)}.
 */
public final class ActiveOriginService {

    private ActiveOriginService() {}

    private static final Identifier CLASS_LAYER =
        com.cyberday1.neoorigins.api.PowerLayers.CLASS_LAYER;

    // ── Per-player power cache ──────────────────────────────────────────
    private static final Map<UUID, CacheEntry> CACHE = new ConcurrentHashMap<>();

    /** Immutable snapshot of a player's resolved (dimension-filtered) power set. */
    private static final class CacheEntry {
        /**
         * Weak handle to the exact {@link ServerPlayer} instance this entry was
         * built against. {@link PlayerOriginData#version()} is {@code transient}
         * and resets to 0 every time the attachment is deserialized, so after a
         * relog the (dimension, dataVersion=0, mgr versions, restrictions)
         * tuple of a brand-new session is byte-for-byte identical to the stale
         * entry left by the previous session — which is keyed by the same UUID.
         * Without this identity guard {@code getOrBuild} would hand a returning
         * player the OLD session's resolved power list (which, if it was built
         * during the disconnect/login window before the datapack managers or
         * the player's own attachment had finished loading, can be empty or
         * partial), so the player keeps their origin but loses most powers until
         * something bumps a version. Tying the entry to the live instance makes
         * a cross-session hit impossible: a new ServerPlayer never {@code ==} the
         * old one, so the entry is rebuilt from the freshly-loaded data.
         */
        final java.lang.ref.WeakReference<ServerPlayer> owner;
        final ResourceKey<Level> dimension;
        final int dataVersion;
        final int originMgrVersion;
        final int powerMgrVersion;
        final int restrictionsVersion;
        final List<PowerHolder<?>> allPowers;
        final List<PowerHolder<?>> originActive;
        final List<PowerHolder<?>> classActive;
        /** Static capability union over every holder NOT in {@link #dynamicCapabilityPowers}. */
        final java.util.Set<String> staticCapabilities;
        final List<PowerHolder<?>> dynamicCapabilityPowers;

        CacheEntry(ServerPlayer owner, ResourceKey<Level> dim, int dv, int omv, int pmv, int rv,
                   List<PowerHolder<?>> all,
                   List<PowerHolder<?>> originActive,
                   List<PowerHolder<?>> classActive) {
            this.owner = new java.lang.ref.WeakReference<>(owner);
            this.dimension = dim;
            this.dataVersion = dv;
            this.originMgrVersion = omv;
            this.powerMgrVersion = pmv;
            this.restrictionsVersion = rv;
            this.allPowers = all;
            this.originActive = originActive;
            this.classActive = classActive;
            java.util.Set<String> caps = new java.util.HashSet<>();
            List<PowerHolder<?>> dynamic = new ArrayList<>();
            indexCapabilities(all, caps, dynamic);
            this.staticCapabilities = java.util.Set.copyOf(caps);
            this.dynamicCapabilityPowers = List.copyOf(dynamic);
        }
    }

    /**
     * Splits {@code holders} into the union of their <em>static</em>
     * {@code capabilities(config)} tags and the holders that must still be asked
     * live. {@code capsOut} is an upper bound over the former only, so it is sound
     * as a negative filter and nothing else.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void indexCapabilities(List<PowerHolder<?>> holders,
                                  java.util.Set<String> capsOut,
                                  List<PowerHolder<?>> dynamicOut) {
        for (PowerHolder<?> holder : holders) {
            if (hasDynamicCapabilities(holder.type())) {
                dynamicOut.add(holder);
            } else {
                capsOut.addAll(((PowerHolder) holder).type().capabilities(holder.config()));
            }
        }
    }

    /**
     * True for types whose static capability set may under-report, either now or
     * the moment {@link #hasCapability} switches to the player-aware call.
     *
     * <p>Two independent criteria, both derived from this branch's own dispatch
     * surface. enhanced_vision reads the content.toml kill-switch and
     * entity_model resolves against MorphDataManager, so their
     * {@code capabilities(config)} answers depend on live state the cache's
     * version tuple does not track — caching either in the union would serve a
     * stale answer. model_color, pose and compat override
     * {@code capabilities(ServerPlayer, Config)}: model_color returns an empty
     * static set whenever a condition is present while its player-aware variant
     * returns the real tag, pose narrows by its own toggle, and compat only ever
     * narrows. None can be folded into a union that is supposed to bound the
     * player-aware result, and CapabilityIndexTest holds that line for any type
     * added later.
     */
    static boolean hasDynamicCapabilities(PowerType<?> type) {
        return isDynamicCapabilityType(type.getClass());
    }

    /** Class-level form, so the exhaustiveness test can classify a type without constructing one. */
    static boolean isDynamicCapabilityType(Class<?> type) {
        return com.cyberday1.neoorigins.power.builtin.ModelColorPower.class.isAssignableFrom(type)
            || com.cyberday1.neoorigins.compat.CompatPower.class.isAssignableFrom(type)
            || com.cyberday1.neoorigins.power.builtin.EnhancedVisionPower.class.isAssignableFrom(type)
            || com.cyberday1.neoorigins.power.builtin.EntityModelPower.class.isAssignableFrom(type)
            || com.cyberday1.neoorigins.power.builtin.PosePower.class.isAssignableFrom(type);
    }

    private static CacheEntry getOrBuild(ServerPlayer player) {
        ResourceKey<Level> dim = player.level().dimension();
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        int dv = data.version();
        int omv = OriginDataManager.INSTANCE.version();
        int pmv = PowerDataManager.INSTANCE.version();
        int rv = AdminConfig.restrictionsVersion();

        UUID uuid = player.getUUID();
        CacheEntry cur = CACHE.get(uuid);
        if (cur != null
            && cur.owner.get() == player
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
        java.util.HashSet<Identifier> seen = new java.util.HashSet<>();
        for (var entry : data.getOrigins().entrySet()) {
            boolean isClassLayer = CLASS_LAYER.equals(entry.getKey());
            Origin origin = OriginDataManager.INSTANCE.getOrigin(entry.getValue());
            if (origin == null) continue;
            // Apply evolution tier overlays — classes don't evolve, only origins
            List<Identifier> effectivePowers = isClassLayer
                ? origin.powers()
                : origin.powersForTier(evolutionTier);
            for (Identifier powerId : effectivePowers) {
                if (AdminConfig.isPowerRestrictedInDimension(powerId, dim)) continue;
                PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                if (holder == null) continue;
                if (!seen.add(powerId)) continue;
                all.add(holder);
                if (holder.occupiesHotkeySlot()
                        && !com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.isNativeHotkeyPower(powerId)) {
                    if (isClassLayer) classActive.add(holder);
                    else originActive.add(holder);
                }
            }
        }
        // Dynamic grants from grant_power action — treated as origin-layer powers.
        for (Identifier powerId : data.getDynamicGrantedPowers()) {
            if (AdminConfig.isPowerRestrictedInDimension(powerId, dim)) continue;
            if (!seen.add(powerId)) continue;
            PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
            if (holder == null) continue;
            all.add(holder);
            if (holder.occupiesHotkeySlot()
                    && !com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.isNativeHotkeyPower(powerId)) {
                originActive.add(holder);
            }
        }

        CacheEntry fresh = new CacheEntry(player, dim, dv, omv, pmv, rv,
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
                // Honour the power's top-level condition gate (power_condition).
                // Powers without a condition are always satisfied; conditioned
                // compat powers (e.g. an invulnerability gated to one dimension)
                // must NOT apply their effect when the condition is unmet. Mirrors
                // PowerHolder.onTick/onHit and hasCapability, which already gate.
                if (!holder.isConditionSatisfied(player)) continue;
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
            // Top-level condition gate (see forEachOfType): a conditioned power
            // whose condition is unmet must not fire.
            if (!holder.isConditionSatisfied(player)) continue;
            if (holder.type() instanceof com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower<?>
                    && ((com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower) holder.type())
                            .isToggledOff(player, holder.config(), holder.id())) {
                continue;
            }
            action.accept((C) holder.config());
        }
    }

    /**
     * True if the player has a toggle power of this type that is granted, has
     * its condition satisfied, and is not currently toggled off.
     *
     * <p>Callers used to write this as {@code has(player, X.class, cfg ->
     * !INSTANCE.isToggledOff(player, cfg))} against a static power instance.
     * That worked while toggle keys came from the config, but since 2.2.24 the
     * key is the power's resource id, and the id is only ambient inside a
     * {@link PowerHolder} dispatch. A predicate gets the config and nothing
     * else, so it would resolve the pre-2.2.24 fallback key and keep reporting
     * the power on after the player had switched it off. Holding the holder is
     * the only way to answer correctly.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean hasToggledOn(ServerPlayer player,
            Class<? extends com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower<?>> typeClass) {
        for (PowerHolder<?> holder : getOrBuild(player).allPowers) {
            if (!typeClass.isInstance(holder.type())) continue;
            if (!holder.isConditionSatisfied(player)) continue;
            if (!((com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower) holder.type())
                    .isToggledOff(player, holder.config(), holder.id())) {
                return true;
            }
        }
        return false;
    }

    /** Returns true if the player has a power of the given type satisfying the predicate. */
    @SuppressWarnings("unchecked")
    public static <C extends PowerConfiguration, T extends PowerType<C>>
    boolean has(ServerPlayer player, Class<T> typeClass, Predicate<C> predicate) {
        for (PowerHolder<?> holder : getOrBuild(player).allPowers) {
            // Honour the power's top-level condition gate (power_condition) so a
            // conditioned power (e.g. invulnerability limited to the pocket
            // dimension) does not report "has" when its condition is unmet.
            // Null condition → always satisfied, so unconditioned native powers
            // are unaffected. Fixes blanket invulnerability from a dimension- or
            // tag-gated origins:invulnerability that compat-translated correctly
            // but was evaluated ungated here.
            if (typeClass.isInstance(holder.type())
                && holder.isConditionSatisfied(player)
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
     *
     * <p>Called per-tick and per-block from several mixins, so the cached static
     * union short-circuits it: a tag absent from the union can only come from a
     * dynamic holder. The union never answers "yes" on its own.
     */
    public static boolean hasCapability(ServerPlayer player, String tag) {
        CacheEntry entry = getOrBuild(player);
        if (!entry.staticCapabilities.contains(tag)) {
            return !entry.dynamicCapabilityPowers.isEmpty()
                && probeCapability(player, tag, entry.dynamicCapabilityPowers);
        }
        return probeCapability(player, tag, entry.allPowers);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean probeCapability(ServerPlayer player, String tag, List<PowerHolder<?>> holders) {
        for (PowerHolder<?> holder : holders) {
            if (!holder.isConditionSatisfied(player)) continue;
            if (holder.type() instanceof com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower<?>
                    && ((com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower) holder.type())
                            .isToggledOff(player, holder.config(), holder.id())) {
                continue;
            }
            // Player-aware variant: runtime-conditioned capabilities (e.g. Route B
            // origins:swimming gated on "in lava") evaluate their gate here, exactly
            // as collectActivePowers does for the client-facing sync.
            if (holder.capabilities(player).contains(tag)) {
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
        java.util.HashSet<Identifier> revoked = new java.util.HashSet<>();
        for (var entry : data.getOrigins().entrySet()) {
            Origin origin = OriginDataManager.INSTANCE.getOrigin(entry.getValue());
            if (origin == null) continue;
            boolean isClassLayer = CLASS_LAYER.equals(entry.getKey());
            List<Identifier> effectivePowers = isClassLayer
                ? origin.powers() : origin.powersForTier(tier);
            for (Identifier powerId : effectivePowers) {
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
        // Clear the global-power ledger too — the dynamic grants backing it were
        // just torn down above, so the ledger must be emptied or reconcilePlayer
        // (run on the next login / datapack-sync) would see the powers as still
        // owned and never re-grant them.
        for (Identifier powerId : new java.util.ArrayList<>(data.getGlobalGrantedPowers())) {
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
    private static java.util.Set<Identifier> collectActivePowerIds(
            ServerPlayer player, Identifier overrideLayer, Identifier overrideOrigin) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        int tier = data.getEvolutionTier();
        java.util.Map<Identifier, Identifier> origins =
            new java.util.HashMap<>(data.getOrigins());
        if (overrideLayer != null) {
            if (overrideOrigin == null) origins.remove(overrideLayer);
            else origins.put(overrideLayer, overrideOrigin);
        }
        java.util.Set<Identifier> ids = new java.util.HashSet<>();
        for (var entry : origins.entrySet()) {
            Origin origin = OriginDataManager.INSTANCE.getOrigin(entry.getValue());
            if (origin == null) continue;
            boolean isClassLayer = CLASS_LAYER.equals(entry.getKey());
            List<Identifier> effectivePowers = isClassLayer
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
    public static void applyOriginPowers(ServerPlayer player, Identifier layerId,
                                          Identifier oldOriginId, Identifier newOriginId) {
        // Snapshot max_health before grant/revoke so we can fill the newly-gained
        // hearts below. attribute_modifier powers raise max_health at grant time
        // but never touch current health, so picking a +HP origin would otherwise
        // leave the bonus hearts empty until natural regen.
        float maxHealthBefore = player.getMaxHealth();
        if (oldOriginId != null) {
            Origin oldOrigin = OriginDataManager.INSTANCE.getOrigin(oldOriginId);
            if (oldOrigin != null) {
                for (Identifier powerId : oldOrigin.powers()) {
                    PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                    if (holder != null) {
                        holder.onRevoked(player);
                        NeoForge.EVENT_BUS.post(new PowerRevokedEvent(player, powerId));
                        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
                            player, com.cyberday1.neoorigins.service.EventPowerIndex.Event.LOST, powerId);
                    }
                }
            }
            // Sweep any orphaned neoorigins attribute modifiers from the old origin
            // in case the JSON was edited or a power was removed since it was granted.
            // Layer-aware: only purge modifiers whose power is no longer active across
            // ANY layer. A blanket wipe here would drop the OTHER layers' attribute
            // boosts (e.g. changing the class layer would erase the origin layer's
            // +HP), since this method only re-grants `newOriginId`'s own powers below.
            // Covers size_scaling's modifiers too, which is why that power's own
            // onRevoked can stay scoped to the ids it granted.
            java.util.Set<Identifier> activePowers =
                collectActivePowerIds(player, layerId, newOriginId);
            com.cyberday1.neoorigins.power.builtin.AttributeModifierPower
                .purgeOriginModifiersExcept(player, activePowers);
        }
        Origin newOrigin = OriginDataManager.INSTANCE.getOrigin(newOriginId);
        if (newOrigin != null) {
            for (Identifier powerId : newOrigin.powers()) {
                PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                if (holder != null) {
                    holder.onGranted(player);
                    NeoForge.EVENT_BUS.post(new PowerGrantedEvent(player, powerId));
                    com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
                        player, com.cyberday1.neoorigins.service.EventPowerIndex.Event.GAINED, powerId);
                }
            }
        }
        // Reconcile current health with the (possibly changed) max_health.
        // If max went UP (e.g. picking a +HP origin), raise current health by the
        // gained delta so the new hearts come in full instead of empty — matching
        // what respawn already does. We add only the delta rather than healing to
        // full, so a lateral/downgrade swap can't be abused as a free full-heal.
        // If max went DOWN (swapping away from +HP), clamp current health to it.
        float maxHealthAfter = player.getMaxHealth();
        if (maxHealthAfter > maxHealthBefore) {
            player.setHealth(Math.min(player.getHealth() + (maxHealthAfter - maxHealthBefore), maxHealthAfter));
        } else if (player.getHealth() > maxHealthAfter) {
            player.setHealth(maxHealthAfter);
        }
    }

    /**
     * Replaces a player's entire layer&rarr;origin selection in one clean
     * transition, tearing down the old powers before granting the new ones.
     * Intended for profile / loadout integrations (e.g. Switchy) that restore a
     * saved origin set: writing the {@link PlayerOriginData} attachment NBT
     * directly leaves the previous origin's powers — attribute modifiers, event
     * handlers, tick state — dangling, because it skips the revoke side of the
     * lifecycle. Call this instead and hand over the new map.
     *
     * <p>Steps, in order:
     * <ol>
     *   <li>{@link #revokeAllPowers(ServerPlayer)} — reads the <em>current</em>
     *       attachment and tears down every active power (so call this BEFORE
     *       overwriting the attachment yourself; this method does it for you).</li>
     *   <li>Overwrites the layer&rarr;origin map with {@code newOrigins}.</li>
     *   <li>{@link #invalidate(UUID)} — drops the resolved-power cache.</li>
     *   <li>Grants each new origin's powers via the grant-only path.</li>
     *   <li>Re-reconciles server-global ({@code apoli:global}) powers the blanket
     *       revoke cleared — they're independent of the chosen origins.</li>
     *   <li>Syncs the fresh state to the client.</li>
     * </ol>
     *
     * <p>Evolution tier and other non-origin scratch state on the attachment are
     * preserved (only the origin selection and power ledgers are rewritten). An
     * empty {@code newOrigins} clears the player to no origins, just like
     * {@code /neoorigins reset}.
     *
     * @param player     the online player to retarget.
     * @param newOrigins the full layer&rarr;origin map to apply (replaces all
     *                   existing layers).
     */
    public static void reapplyOrigins(ServerPlayer player,
                                      Map<Identifier, Identifier> newOrigins) {
        // 1. Tear down the current (old) power state from the existing attachment.
        revokeAllPowers(player);

        // 2. Overwrite the layer→origin map with the incoming profile.
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        data.clear();
        newOrigins.forEach(data::setOrigin);
        // A populated profile means the player has made their selections — set the
        // flag so the next login doesn't re-prompt the origin picker. clear() above
        // reset it to false.
        if (!newOrigins.isEmpty()) {
            data.setHadAllOrigins(true);
        }

        // 3. Drop the resolved-power cache so the next read rebuilds from the new map.
        invalidate(player.getUUID());

        // 4. Grant the new origins' powers (oldOrigin = null → grant-only path).
        newOrigins.forEach((layer, origin) ->
            applyOriginPowers(player, layer, null, origin));

        // 5. Restore server-global powers the blanket revoke cleared.
        GlobalPowerService.reconcilePlayer(player);

        // 6. Push the fresh state to the client (HUD, keybinds, active powers).
        com.cyberday1.neoorigins.network.NeoOriginsNetwork.syncToPlayer(player);
    }
}
