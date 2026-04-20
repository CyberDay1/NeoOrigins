package com.cyberday1.neoorigins.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Stores a player's chosen origin per layer.
 * Persisted via Codec (no raw CompoundTag writes — forward-compatible through 1.21.6+).
 */
public class PlayerOriginData {

    private final Map<ResourceLocation, ResourceLocation> origins = new TreeMap<>();
    private boolean hadAllOrigins = false;
    /** Tracks power grant_ids for StartingEquipmentPower — persisted so items aren't duplicated on respawn. */
    private final Set<String> grantedEquipmentPowers = new HashSet<>();
    /** Positions of placed shadow orbs for ShadowOrbPower — persisted. */
    private final List<BlockPos> shadowOrbs = new ArrayList<>();
    /** How many times this player has used the Orb of Origin — persisted, drives escalating XP cost. */
    private int orbUseCount = 0;
    /** Persisted toggle-off state for AbstractTogglePower — keyed by power toggle key. */
    private final Set<String> toggledOffPowers = new HashSet<>();
    /** Powers granted at runtime via action grant_power (not tied to any origin). Persisted. */
    private final Set<ResourceLocation> dynamicGrantedPowers = new HashSet<>();
    /** Session-only — not serialized. Maps power type id → server tick when cooldown expires. */
    private final Map<String, Integer> activeCooldowns = new HashMap<>();
    /** Session-only — not serialized. Bumped on any mutation that affects the active power set;
     *  used by ActiveOriginService's per-player power cache for invalidation. */
    private transient int version = 0;

    public static final Codec<PlayerOriginData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.unboundedMap(ResourceLocation.CODEC, ResourceLocation.CODEC)
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
        ResourceLocation.CODEC.listOf()
            .optionalFieldOf("dynamic_granted_powers", List.of())
            .forGetter(d -> new ArrayList<>(d.dynamicGrantedPowers))
    ).apply(inst, (map, hadAll, equipment, orbs, orbUses, toggledOff, dynamic) -> {
        PlayerOriginData data = new PlayerOriginData();
        data.origins.putAll(map);
        data.hadAllOrigins = hadAll;
        data.grantedEquipmentPowers.addAll(equipment);
        data.shadowOrbs.addAll(orbs);
        data.orbUseCount = orbUses;
        data.toggledOffPowers.addAll(toggledOff);
        data.dynamicGrantedPowers.addAll(dynamic);
        return data;
    }));

    public Map<ResourceLocation, ResourceLocation> getOrigins() {
        return Collections.unmodifiableMap(origins);
    }

    public ResourceLocation getOrigin(ResourceLocation layerId) {
        return origins.get(layerId);
    }

    public void setOrigin(ResourceLocation layerId, ResourceLocation originId) {
        origins.put(layerId, originId);
        version++;
    }

    public void removeOrigin(ResourceLocation layerId) {
        origins.remove(layerId);
        version++;
    }

    public boolean hasOriginForLayer(ResourceLocation layerId) {
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

    public List<BlockPos> getShadowOrbs() {
        return List.copyOf(shadowOrbs);
    }

    public void setShadowOrbs(List<BlockPos> orbs) {
        shadowOrbs.clear();
        shadowOrbs.addAll(orbs);
    }

    public int getOrbUseCount() { return orbUseCount; }
    public void incrementOrbUseCount() { orbUseCount++; }

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
    public Set<ResourceLocation> getDynamicGrantedPowers() {
        return Collections.unmodifiableSet(dynamicGrantedPowers);
    }

    public boolean hasDynamicGrant(ResourceLocation powerId) {
        return dynamicGrantedPowers.contains(powerId);
    }

    /** @return true if the set changed (i.e. power was newly granted). */
    public boolean addDynamicGrant(ResourceLocation powerId) {
        boolean added = dynamicGrantedPowers.add(powerId);
        if (added) version++;
        return added;
    }

    /** @return true if the set changed (i.e. power was actually removed). */
    public boolean removeDynamicGrant(ResourceLocation powerId) {
        boolean removed = dynamicGrantedPowers.remove(powerId);
        if (removed) version++;
        return removed;
    }

    public void clear() {
        origins.clear();
        hadAllOrigins = false;
        grantedEquipmentPowers.clear();
        shadowOrbs.clear();
        toggledOffPowers.clear();
        dynamicGrantedPowers.clear();
        activeCooldowns.clear();
        version++;
    }

    /** Version counter for ActiveOriginService's per-player power cache. Bumped on every mutation. */
    public int version() { return version; }
}
