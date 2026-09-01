package com.cyberday1.neoorigins.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores a player's chosen origin per layer.
 * Persisted via Codec (no raw CompoundTag writes — forward-compatible through 1.21.6+).
 */
public class PlayerOriginData {

    private final Map<Identifier, Identifier> origins = new TreeMap<>();
    private boolean hadAllOrigins = false;
    /** Tracks power grant_ids for StartingEquipmentPower — persisted so items aren't duplicated on respawn. */
    private final Set<String> grantedEquipmentPowers = new HashSet<>();
    /** Positions of placed shadow orbs for ShadowOrbPower — persisted. */
    private final List<BlockPos> shadowOrbs = new ArrayList<>();
    /** Tracks how many times the player has used an Orb of Origin — persisted for escalating XP cost. */
    private int orbUseCount = 0;
    /** Persisted toggle-off state for AbstractTogglePower — keyed by power toggle key. */
    private final Set<String> toggledOffPowers = new HashSet<>();
    /** Powers granted at runtime via action grant_power (not tied to any origin). Persisted. */
    private final Set<Identifier> dynamicGrantedPowers = new HashSet<>();
    /** Powers granted by global power sets (apoli:global port). A ledger of what the
     *  global system owns, so it can reconcile (revoke powers no longer offered by any
     *  matching global set) without disturbing action-granted dynamics. Persisted. */
    private final Set<Identifier> globalGrantedPowers = new HashSet<>();
    /** Named UUID sets (entity_set power + in_set / add_to_set / remove_from_set verbs). Persisted as Map&lt;String, List&lt;String&gt;&gt;. */
    private final Map<String, Set<UUID>> entitySets = new HashMap<>();
    /** Generic keyed float storage for power types that need persisted numeric state (e.g. SlimeMoisturePower). */
    private final Map<String, Float> customFloats = new HashMap<>();
    /** Essence evolution: mob kills accumulated toward the next evolution tier. Persisted. */
    private int essenceKills = 0;
    /** Essence evolution: current tier (0 = base, 1 = Evolved, 2 = Ascended, 3 = Apex). Persisted. */
    private int evolutionTier = 0;
    /** Essence evolution: the tier this player declined, or 0 for none. The kill
     *  threshold that fires an evolution prompt stays satisfied on every later
     *  kill, so without this the prompt re-fired forever once declined. Persisted
     *  because the suppression has to survive a relog; cleared whenever the tier
     *  is accepted, force-set or reset, so a stale flag can't mute a legitimate
     *  future prompt. */
    private int declinedEvolutionTier = 0;
    /** Session-only — not serialized. Maps power type id → server tick when cooldown expires. */
    private final Map<String, Integer> activeCooldowns = new ConcurrentHashMap<>();
    /** Session-only — not serialized. Bumped on any mutation that affects the active power set;
     *  used by ActiveOriginService's per-player power cache for invalidation. */
    private transient int version = 0;
    /** Session-only — true while an orb-of-origin picker is open and the reset hasn't
     *  been committed yet. The first successful ChooseOrigin after this flag is set
     *  performs the actual revoke/XP/stack-shrink; picker-close clears it. */
    private transient boolean pendingOrbCommit = false;
    /** Session-only — true while a scoped layer picker is open and its deferred
     *  commit (XP cost + optional class-orb consume) hasn't fired yet. Unlike
     *  {@link #pendingOrbCommit} the scoped layers have already been cleared (so the
     *  picker shows only them); the first successful ChooseOrigin performs the
     *  deferred work, and a picker-close restores {@link #pendingLayerPickerPrev}.
     *  Generalizes the former Orb-of-Class flow to any layer subset (the
     *  {@code neoorigins:open_layer_picker} action, {@code /origin gui <player> <layer>},
     *  and the Orb of Class all route through it). */
    private transient boolean pendingLayerPickerCommit = false;
    /** Session-only — the prior origin of each scoped layer that HAD one before the
     *  picker opened, so a cancelled pick can be rolled back layer-by-layer. */
    private transient Map<Identifier, Identifier> pendingLayerPickerPrev = new HashMap<>();
    /** Session-only — XP levels to charge when the scoped picker's first pick commits. */
    private transient int pendingLayerPickerCost = 0;
    /** Session-only — if non-null, one of this item is removed from the player's
     *  inventory when the scoped picker's pick commits. The Orb of Class stores
     *  itself here; the {@code open_layer_picker} action stores the held item when
     *  {@code consume_item} is set. Null = consume nothing. */
    private transient net.minecraft.world.item.Item pendingLayerPickerConsumeItem = null;
    /** Session-only — true while an OP-initiated re-selection picker is open
     *  ({@code /origin gui <player>}). Authorizes the target (a non-OP player)
     *  to change an already-chosen origin for the duration of that picker
     *  session; cleared when the picker is abandoned or selection re-completes.
     *  Without it, a non-OP could reset their own origin for free via the
     *  picker or a crafted ChooseOrigin packet. */
    private transient boolean pendingAdminReselect = false;
    /** Set when the player closes the origin picker without committing any
     *  origin. Disables first-pick invulnerability so they can't stay
     *  immortal forever by dismissing the picker. Persisted so the flag
     *  survives relog. Cleared on the next successful ChooseOrigin. */
    private boolean pickerAbandoned = false;
    /** Player-facing night-vision master switch, flipped by the dedicated
     *  "Toggle Night Vision" keybind. Defaults to {@code true} so a player who
     *  never presses the key sees the historical always-on behaviour. Gates every
     *  {@code minecraft:night_vision} persistent effect at once (so multi-tier
     *  origins take one keypress, not one per tier) and the client-side
     *  {@code enhanced_vision} brightness boost. Persisted here — and the
     *  attachment is {@code copyOnDeath} — so the choice survives relog AND death.
     *  Deliberately NOT part of {@link #toggledOffPowers}: this is a player
     *  preference, not a power toggle, and it must never be cleared by an
     *  origin reset or a stray skill keypress. */
    private boolean nightVisionEnabled = true;

    public static final Codec<PlayerOriginData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.unboundedMap(Identifier.CODEC, Identifier.CODEC)
            .optionalFieldOf("origins", Map.of())
            .forGetter(d -> Map.copyOf(d.origins)),
        Codec.BOOL
            .optionalFieldOf("had_all_origins", false)
            .forGetter(d -> d.hadAllOrigins),
        Codec.STRING.listOf()
            .optionalFieldOf("granted_equipment", List.of())
            .forGetter(d -> new ArrayList<>(d.grantedEquipmentPowers)),
        BlockPos.CODEC.listOf()
            .optionalFieldOf("shadow_orbs", List.of())
            .forGetter(d -> List.copyOf(d.shadowOrbs)),
        Codec.INT
            .optionalFieldOf("orb_use_count", 0)
            .forGetter(d -> d.orbUseCount),
        Codec.STRING.listOf()
            .optionalFieldOf("toggled_off_powers", List.of())
            .forGetter(d -> new ArrayList<>(d.toggledOffPowers)),
        Identifier.CODEC.listOf()
            .optionalFieldOf("dynamic_granted_powers", List.of())
            .forGetter(d -> new ArrayList<>(d.dynamicGrantedPowers)),
        Identifier.CODEC.listOf()
            .optionalFieldOf("global_granted_powers", List.of())
            .forGetter(d -> new ArrayList<>(d.globalGrantedPowers)),
        Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())
            .optionalFieldOf("entity_sets", Map.of())
            .forGetter(d -> {
                Map<String, List<String>> out = new LinkedHashMap<>();
                for (var e : d.entitySets.entrySet()) {
                    List<String> uuids = new ArrayList<>(e.getValue().size());
                    for (UUID u : e.getValue()) uuids.add(u.toString());
                    out.put(e.getKey(), uuids);
                }
                return out;
            }),
        Codec.unboundedMap(Codec.STRING, Codec.FLOAT)
            .optionalFieldOf("custom_floats", Map.of())
            .forGetter(d -> Map.copyOf(d.customFloats)),
        Codec.INT
            .optionalFieldOf("essence_kills", 0)
            .forGetter(d -> d.essenceKills),
        Codec.INT
            .optionalFieldOf("evolution_tier", 0)
            .forGetter(d -> d.evolutionTier),
        Codec.INT
            .optionalFieldOf("declined_evolution_tier", 0)
            .forGetter(d -> d.declinedEvolutionTier),
        Codec.BOOL
            .optionalFieldOf("picker_abandoned", false)
            .forGetter(d -> d.pickerAbandoned),
        // Default TRUE: an absent key (every pre-existing save, and every player
        // who never touches the keybind) must decode as night-vision-on, which is
        // exactly the pre-toggle behaviour. Never write a "false" default here.
        Codec.BOOL
            .optionalFieldOf("night_vision_enabled", true)
            .forGetter(d -> d.nightVisionEnabled)
    ).apply(inst, (map, hadAll, equipment, orbs, orbUses, toggledOff, dynamic, global, sets, floats, kills, tier, declinedTier, abandoned, nightVision) -> {
        PlayerOriginData data = new PlayerOriginData();
        // Canonicalize any renamed origin selections (e.g. jianxian → sword_immortal)
        // so saved worlds keep their chosen origin after the rename.
        map.forEach((layer, origin) ->
            data.origins.put(layer, com.cyberday1.neoorigins.data.LegacyOriginIds.remap(origin)));
        // Pre-v2.1.2 stored the canonical origin layer as "origins:origin".
        // Forward-migrate to the new canonical "neoorigins:origin" so saved
        // selections survive the rename; if both keys exist (re-pick on the
        // new build before this load), the canonical entry wins.
        Identifier legacy = Identifier.fromNamespaceAndPath("origins", "origin");
        Identifier canonical = Identifier.fromNamespaceAndPath("neoorigins", "origin");
        Identifier legacyValue = data.origins.remove(legacy);
        if (legacyValue != null && !data.origins.containsKey(canonical)) {
            data.origins.put(canonical, legacyValue);
        }
        data.hadAllOrigins = hadAll;
        data.grantedEquipmentPowers.addAll(equipment);
        data.shadowOrbs.addAll(orbs);
        data.orbUseCount = orbUses;
        data.toggledOffPowers.addAll(toggledOff);
        data.dynamicGrantedPowers.addAll(dynamic);
        data.globalGrantedPowers.addAll(global);
        for (var e : sets.entrySet()) {
            Set<UUID> parsed = new LinkedHashSet<>();
            for (String s : e.getValue()) {
                try { parsed.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
            }
            if (!parsed.isEmpty()) data.entitySets.put(e.getKey(), parsed);
        }
        data.customFloats.putAll(floats);
        data.essenceKills = kills;
        data.evolutionTier = tier;
        data.declinedEvolutionTier = declinedTier;
        data.pickerAbandoned = abandoned;
        data.nightVisionEnabled = nightVision;
        return data;
    }));

    // ── Custom float storage ───────────────────────────────────────────

    public float getCustomFloat(String key, float defaultValue) {
        return customFloats.getOrDefault(key, defaultValue);
    }

    public void setCustomFloat(String key, float value) {
        customFloats.put(key, value);
        // Intentionally does NOT bump `version`. customFloats is per-tick
        // scratch state (moisture, cooldown/recovery counters, key-edge
        // flags) that never changes WHICH powers are granted/active — the
        // only thing `version` keys (ActiveOriginService's resolved-power
        // cache). Several powers write a custom float every tick; bumping
        // here rebuilt that cache every tick for those players. Power
        // conditions are evaluated live, not cached, so a float-driven
        // condition still re-reads the new value.
    }

    // ── Essence evolution ──────────────────────────────────────────────

    public int getEssenceKills() { return essenceKills; }

    public void setEssenceKills(int kills) {
        this.essenceKills = kills;
        version++;
    }

    public int incrementEssenceKills() {
        essenceKills++;
        version++;
        return essenceKills;
    }

    public int getEvolutionTier() { return evolutionTier; }

    public void setEvolutionTier(int tier) {
        this.evolutionTier = Math.max(0, Math.min(3, tier));
        version++;
    }

    /** The tier the player declined, or 0 if they haven't declined one. */
    public int getDeclinedEvolutionTier() { return declinedEvolutionTier; }

    /** Pass 0 to clear the suppression. */
    public void setDeclinedEvolutionTier(int tier) {
        this.declinedEvolutionTier = Math.max(0, Math.min(3, tier));
        version++;
    }

    public void resetEvolution() {
        this.essenceKills = 0;
        this.evolutionTier = 0;
        this.declinedEvolutionTier = 0;
        version++;
    }

    public Map<Identifier, Identifier> getOrigins() {
        return Collections.unmodifiableMap(origins);
    }

    public Identifier getOrigin(Identifier layerId) {
        return origins.get(layerId);
    }

    public void setOrigin(Identifier layerId, Identifier originId) {
        origins.put(layerId, originId);
        version++;
    }

    public void removeOrigin(Identifier layerId) {
        origins.remove(layerId);
        version++;
    }

    public boolean hasOriginForLayer(Identifier layerId) {
        return origins.containsKey(layerId);
    }

    public boolean isHadAllOrigins() { return hadAllOrigins; }
    public void setHadAllOrigins(boolean hadAllOrigins) { this.hadAllOrigins = hadAllOrigins; }

    public boolean isOnCooldown(String typeId, int currentTick) {
        Integer expiresAt = activeCooldowns.get(typeId);
        return expiresAt != null && currentTick < expiresAt;
    }

    /**
     * Player-aware cooldown check used by activation gates: returns false when
     * the player should bypass cooldowns (Creative mode, per
     * {@link com.cyberday1.neoorigins.config.GameplayConfig#creativeCooldownBypass}),
     * otherwise delegates to {@link #isOnCooldown(String, int)} with the player's
     * current tick. Cooldowns are still recorded via {@link #setCooldown}, so the
     * bypass only suppresses the gate while Creative.
     */
    public boolean isOnCooldown(net.minecraft.world.entity.player.Player player, String typeId) {
        if (com.cyberday1.neoorigins.config.GameplayConfig.creativeCooldownBypass(player)) return false;
        return isOnCooldown(typeId, player.tickCount);
    }

    public void setCooldown(String typeId, int currentTick, int durationTicks) {
        activeCooldowns.put(typeId, currentTick + durationTicks);
    }

    public int remainingCooldown(String typeId, int currentTick) {
        Integer expiresAt = activeCooldowns.get(typeId);
        if (expiresAt == null) return 0;
        return Math.max(0, expiresAt - currentTick);
    }

    public boolean hasGrantedEquipment(String grantId) {
        return grantedEquipmentPowers.contains(grantId);
    }

    public void markEquipmentGranted(String grantId) {
        grantedEquipmentPowers.add(grantId);
    }

    /** Reset the equipment-grant ledger so a re-pick (orb / admin reset) can re-grant items. */
    public void clearGrantedEquipment() {
        grantedEquipmentPowers.clear();
    }

    public List<BlockPos> getShadowOrbs() {
        return List.copyOf(shadowOrbs);
    }

    public void setShadowOrbs(List<BlockPos> orbs) {
        shadowOrbs.clear();
        shadowOrbs.addAll(orbs);
    }

    public int getOrbUseCount() {
        return orbUseCount;
    }

    public void incrementOrbUseCount() {
        orbUseCount++;
    }

    public boolean isPendingOrbCommit() { return pendingOrbCommit; }
    public void setPendingOrbCommit(boolean pending) { this.pendingOrbCommit = pending; }

    public boolean isPendingLayerPickerCommit() { return pendingLayerPickerCommit; }
    public void setPendingLayerPickerCommit(boolean pending) { this.pendingLayerPickerCommit = pending; }

    public Map<Identifier, Identifier> getPendingLayerPickerPrev() { return pendingLayerPickerPrev; }
    public void setPendingLayerPickerPrev(Map<Identifier, Identifier> prev) {
        this.pendingLayerPickerPrev = prev == null ? new HashMap<>() : new HashMap<>(prev);
    }

    public int getPendingLayerPickerCost() { return pendingLayerPickerCost; }
    public void setPendingLayerPickerCost(int cost) { this.pendingLayerPickerCost = cost; }

    public net.minecraft.world.item.Item getPendingLayerPickerConsumeItem() { return pendingLayerPickerConsumeItem; }
    public void setPendingLayerPickerConsumeItem(net.minecraft.world.item.Item item) { this.pendingLayerPickerConsumeItem = item; }

    public boolean isPendingAdminReselect() { return pendingAdminReselect; }
    public void setPendingAdminReselect(boolean pending) { this.pendingAdminReselect = pending; }

    public boolean isPickerAbandoned() { return pickerAbandoned; }
    public void setPickerAbandoned(boolean abandoned) { this.pickerAbandoned = abandoned; }

    /** Player's night-vision master switch; {@code true} (on) unless they've
     *  pressed the dedicated keybind to turn it off. */
    public boolean isNightVisionEnabled() { return nightVisionEnabled; }

    public void setNightVisionEnabled(boolean enabled) { this.nightVisionEnabled = enabled; }

    public boolean isPowerToggledOff(String toggleKey) {
        return toggledOffPowers.contains(toggleKey);
    }

    public void setPowerToggledOff(String toggleKey, boolean off) {
        if (off) toggledOffPowers.add(toggleKey);
        else toggledOffPowers.remove(toggleKey);
    }

    /**
     * Toggle state, reading the power's own key and falling back to the key
     * shape used before 2.2.24.
     *
     * <p>Toggle keys used to be derived from the power's CONFIG rather than its
     * id, so two powers of one type could share a single flag — that was the
     * bug. Keys are now the power's resource id, which orphans every flag
     * already written into a save. Reading the legacy key as a fallback is what
     * stops a player's toggles all springing back on when they update.
     *
     * <p>Reading rather than rewriting on load is deliberate. Where the old key
     * was shared, both powers still see "off", which is what the player last
     * chose; a migration pass would have to pick one of them. The legacy entry
     * is dropped by {@link #setPowerToggledOff(String, String, boolean)} the
     * first time either power is toggled, so saves heal as they are played.
     */
    public boolean isPowerToggledOff(String toggleKey, String legacyKey) {
        return toggledOffPowers.contains(toggleKey)
            || (legacyKey != null && toggledOffPowers.contains(legacyKey));
    }

    /**
     * Writes the toggle flag under the power's own key and retires the legacy
     * key at the same time, so the fallback above stops applying to this power
     * once the player has actually used it.
     */
    public void setPowerToggledOff(String toggleKey, String legacyKey, boolean off) {
        if (legacyKey != null && !legacyKey.equals(toggleKey)) toggledOffPowers.remove(legacyKey);
        setPowerToggledOff(toggleKey, off);
    }

    public void clearToggles() {
        toggledOffPowers.clear();
    }

    /** Dynamic grants: powers added at runtime via the grant_power action. */
    public Set<Identifier> getDynamicGrantedPowers() {
        return Collections.unmodifiableSet(dynamicGrantedPowers);
    }

    public boolean hasDynamicGrant(Identifier powerId) {
        return dynamicGrantedPowers.contains(powerId);
    }

    /** @return true if the set changed (i.e. power was newly granted). */
    public boolean addDynamicGrant(Identifier powerId) {
        boolean added = dynamicGrantedPowers.add(powerId);
        if (added) version++;
        return added;
    }

    /** @return true if the set changed (i.e. power was actually removed). */
    public boolean removeDynamicGrant(Identifier powerId) {
        boolean removed = dynamicGrantedPowers.remove(powerId);
        if (removed) version++;
        return removed;
    }

    // ── Global power sets (apoli:global port) ───────────────────────────

    /** The global-power ledger: powers currently owned by matching global power sets. */
    public Set<Identifier> getGlobalGrantedPowers() {
        return Collections.unmodifiableSet(globalGrantedPowers);
    }

    public boolean hasGlobalGrant(Identifier powerId) {
        return globalGrantedPowers.contains(powerId);
    }

    /** @return true if the ledger changed. */
    public boolean addGlobalGrant(Identifier powerId) {
        boolean added = globalGrantedPowers.add(powerId);
        if (added) version++;
        return added;
    }

    /** @return true if the ledger changed. */
    public boolean removeGlobalGrant(Identifier powerId) {
        boolean removed = globalGrantedPowers.remove(powerId);
        if (removed) version++;
        return removed;
    }

    // ---- Named UUID sets (entity_set power + in_set / add_to_set / remove_from_set verbs) ----

    /**
     * Returns an unmodifiable snapshot of the named UUID set, or an empty set if unknown.
     * The snapshot is NOT GC'd — use {@link #addToEntitySet}/{@link #removeFromEntitySet}
     * for mutating calls (which GC on every write).
     */
    public Set<UUID> getEntitySet(String name) {
        Set<UUID> s = entitySets.get(name);
        return s == null ? Collections.emptySet() : Collections.unmodifiableSet(s);
    }

    /**
     * Add a UUID to the named set, then sweep out any UUIDs in that set whose entity has
     * despawned or died in {@code sp.level()}. Creates the set if it doesn't exist.
     */
    public void addToEntitySet(ServerPlayer sp, String name, UUID uuid) {
        Set<UUID> set = entitySets.computeIfAbsent(name, k -> new LinkedHashSet<>());
        set.add(uuid);
        gcSet(sp, set);
        version++;
    }

    /**
     * Remove a UUID from the named set, then sweep out any despawned UUIDs.
     * No-op if the set doesn't exist.
     */
    public void removeFromEntitySet(ServerPlayer sp, String name, UUID uuid) {
        Set<UUID> set = entitySets.get(name);
        if (set == null) return;
        set.remove(uuid);
        gcSet(sp, set);
        if (set.isEmpty()) entitySets.remove(name);
        version++;
    }

    /** Drop the entire named set. */
    public void clearEntitySet(String name) {
        if (entitySets.remove(name) != null) version++;
    }

    private static void gcSet(ServerPlayer sp, Set<UUID> set) {
        if (sp == null) return;
        var minecraftServer = sp.level().getServer();
        if (minecraftServer == null) return;
        // Search all loaded dimensions so entities in other dimensions aren't
        // falsely treated as despawned and pruned from the set.
        set.removeIf(u -> {
            for (var level : minecraftServer.getAllLevels()) {
                if (level.getEntity(u) != null) return false;
            }
            return true;
        });
    }

    public void clear() {
        origins.clear();
        hadAllOrigins = false;
        pendingAdminReselect = false;
        grantedEquipmentPowers.clear();
        shadowOrbs.clear();
        toggledOffPowers.clear();
        dynamicGrantedPowers.clear();
        globalGrantedPowers.clear();
        entitySets.clear();
        activeCooldowns.clear();
        // nightVisionEnabled is deliberately NOT reset: it's a player preference
        // (like a control binding), not origin state. Wiping it on an origin
        // reset would silently flip night vision back on for someone who chose
        // to turn it off — exactly the "it resets itself" failure we're avoiding.
        version++;
    }

    /** Version counter for ActiveOriginService's per-player power cache. Bumped on every mutation. */
    public int version() { return version; }
}
