package com.cyberday1.neoorigins.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores a player's chosen origin per layer.
 * Persisted via Codec (no raw CompoundTag writes — forward-compatible through 1.21.6+).
 */
public class PlayerOriginData {

    private final Map<Identifier, Identifier> origins = new HashMap<>();
    private boolean hadAllOrigins = false;
    /** Session-only — not serialized. Maps power type id → server tick when cooldown expires. */
    private final Map<String, Integer> activeCooldowns = new ConcurrentHashMap<>();

    public static final Codec<PlayerOriginData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.unboundedMap(Identifier.CODEC, Identifier.CODEC)
            .optionalFieldOf("origins", Map.of())
            .forGetter(d -> Map.copyOf(d.origins)),
        Codec.BOOL
            .optionalFieldOf("had_all_origins", false)
            .forGetter(d -> d.hadAllOrigins)
    ).apply(inst, (map, hadAll) -> {
        PlayerOriginData data = new PlayerOriginData();
        data.origins.putAll(map);
        data.hadAllOrigins = hadAll;
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

    public void clear() {
        origins.clear();
        hadAllOrigins = false;
        activeCooldowns.clear();
    }
}
