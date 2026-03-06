package com.cyberday1.neoorigins.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores a player's chosen origin per layer.
 * Persisted via Codec (no raw CompoundTag writes — forward-compatible through 1.21.6+).
 */
public class PlayerOriginData {

    private final Map<Identifier, Identifier> origins = new LinkedHashMap<>();
    private boolean hadAllOrigins = false;
    /** Tracks power grant_ids for StartingEquipmentPower — persisted so items aren't duplicated on respawn. */
    private final Set<String> grantedEquipmentPowers = new HashSet<>();
    /** Positions of placed shadow orbs for ShadowOrbPower — persisted. */
    private final List<BlockPos> shadowOrbs = new ArrayList<>();
    /** Session-only — not serialized. Maps power type id → server tick when cooldown expires. */
    private final Map<String, Integer> activeCooldowns = new ConcurrentHashMap<>();

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
            .forGetter(d -> List.copyOf(d.shadowOrbs))
    ).apply(inst, (map, hadAll, equipment, orbs) -> {
        PlayerOriginData data = new PlayerOriginData();
        data.origins.putAll(map);
        data.hadAllOrigins = hadAll;
        data.grantedEquipmentPowers.addAll(equipment);
        data.shadowOrbs.addAll(orbs);
        return data;
    }));

    public Map<Identifier, Identifier> getOrigins() {
        return Map.copyOf(origins);
    }

    public Identifier getOrigin(Identifier layerId) {
        return origins.get(layerId);
    }

    public void setOrigin(Identifier layerId, Identifier originId) {
        origins.put(layerId, originId);
    }

    public void removeOrigin(Identifier layerId) {
        origins.remove(layerId);
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

    public List<BlockPos> getShadowOrbs() {
        return List.copyOf(shadowOrbs);
    }

    public void setShadowOrbs(List<BlockPos> orbs) {
        shadowOrbs.clear();
        shadowOrbs.addAll(orbs);
    }

    public void clear() {
        origins.clear();
        hadAllOrigins = false;
        grantedEquipmentPowers.clear();
        shadowOrbs.clear();
        activeCooldowns.clear();
    }
}
