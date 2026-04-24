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
    /** Named UUID sets (entity_set power + in_set / add_to_set / remove_from_set verbs). Persisted as Map&lt;String, List&lt;String&gt;&gt;. */
    private final Map<String, Set<UUID>> entitySets = new HashMap<>();
    /** Session-only — not serialized. Maps power type id → server tick when cooldown expires. */
    private final Map<String, Integer> activeCooldowns = new ConcurrentHashMap<>();
    /** Session-only — not serialized. Bumped on any mutation that affects the active power set;
     *  used by ActiveOriginService's per-player power cache for invalidation. */
    private transient int version = 0;
    /** Session-only — true while an orb-of-origin picker is open and the reset hasn't
     *  been committed yet. The first successful ChooseOrigin after this flag is set
     *  performs the actual revoke/XP/stack-shrink; picker-close clears it. */
    private transient boolean pendingOrbCommit = false;
    /** Session-only — set when the player closes the origin picker without
     *  committing any origin. Disables first-pick invulnerability so they
     *  can't stay immortal forever by dismissing the picker. Cleared on the
     *  next successful ChooseOrigin. */
    private transient boolean pickerAbandoned = false;

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
            })
    ).apply(inst, (map, hadAll, equipment, orbs, orbUses, toggledOff, dynamic, sets) -> {
        PlayerOriginData data = new PlayerOriginData();
        data.origins.putAll(map);
        data.hadAllOrigins = hadAll;
        data.grantedEquipmentPowers.addAll(equipment);
        data.shadowOrbs.addAll(orbs);
        data.orbUseCount = orbUses;
        data.toggledOffPowers.addAll(toggledOff);
        data.dynamicGrantedPowers.addAll(dynamic);
        for (var e : sets.entrySet()) {
            Set<UUID> parsed = new LinkedHashSet<>();
            for (String s : e.getValue()) {
                try { parsed.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
            }
            if (!parsed.isEmpty()) data.entitySets.put(e.getKey(), parsed);
        }
        return data;
    }));

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

    public boolean isPickerAbandoned() { return pickerAbandoned; }
    public void setPickerAbandoned(boolean abandoned) { this.pickerAbandoned = abandoned; }

    public boolean isPowerToggledOff(String toggleKey) {
        return toggledOffPowers.contains(toggleKey);
    }

    public void setPowerToggledOff(String toggleKey, boolean off) {
        if (off) toggledOffPowers.add(toggleKey);
        else toggledOffPowers.remove(toggleKey);
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
        if (sp == null || sp.level() == null) return;
        set.removeIf(u -> sp.level().getEntity(u) == null);
    }

    public void clear() {
        origins.clear();
        hadAllOrigins = false;
        grantedEquipmentPowers.clear();
        shadowOrbs.clear();
        toggledOffPowers.clear();
        dynamicGrantedPowers.clear();
        entitySets.clear();
        activeCooldowns.clear();
        version++;
    }

    /** Version counter for ActiveOriginService's per-player power cache. Bumped on every mutation. */
    public int version() { return version; }
}
