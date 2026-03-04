package com.cyberday1.neoorigins.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores a player's chosen origin per layer.
 * Persisted via Codec (no raw CompoundTag writes — forward-compatible through 1.21.6+).
 */
public class PlayerOriginData {

    private final Map<ResourceLocation, ResourceLocation> origins = new HashMap<>();
    private boolean hadAllOrigins = false;

    public static final Codec<PlayerOriginData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.unboundedMap(ResourceLocation.CODEC, ResourceLocation.CODEC)
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

    public Map<ResourceLocation, ResourceLocation> getOrigins() {
        return Map.copyOf(origins);
    }

    public ResourceLocation getOrigin(ResourceLocation layerId) {
        return origins.get(layerId);
    }

    public void setOrigin(ResourceLocation layerId, ResourceLocation originId) {
        origins.put(layerId, originId);
    }

    public void removeOrigin(ResourceLocation layerId) {
        origins.remove(layerId);
    }

    public boolean hasOriginForLayer(ResourceLocation layerId) {
        return origins.containsKey(layerId);
    }

    public boolean isHadAllOrigins() { return hadAllOrigins; }
    public void setHadAllOrigins(boolean hadAllOrigins) { this.hadAllOrigins = hadAllOrigins; }

    public void clear() {
        origins.clear();
        hadAllOrigins = false;
    }
}
