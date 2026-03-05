package com.cyberday1.neoorigins.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores a player's chosen origin per layer.
 * Persisted via Codec (no raw CompoundTag writes — forward-compatible through 1.21.6+).
 */
public class PlayerOriginData {

    private final Map<Identifier, Identifier> origins = new HashMap<>();
    private boolean hadAllOrigins = false;

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

    public void clear() {
        origins.clear();
        hadAllOrigins = false;
    }
}
