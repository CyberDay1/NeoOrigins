package com.cyberday1.neoorigins.api.origin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

public record OriginLayer(
    ResourceLocation id,
    int order,
    List<ConditionedOrigin> origins,
    boolean enabled,
    Component name,
    boolean allowRandom,
    Optional<ResourceLocation> defaultOrigin,
    boolean autoChoose,
    boolean hidden,
    List<ResourceLocation> excludeRandom
) {
    public static final Codec<OriginLayer> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        ResourceLocation.CODEC.fieldOf("id").forGetter(OriginLayer::id),
        Codec.INT.optionalFieldOf("order", 0).forGetter(OriginLayer::order),
        ConditionedOrigin.CODEC.listOf().optionalFieldOf("origins", List.of()).forGetter(OriginLayer::origins),
        Codec.BOOL.optionalFieldOf("enabled", true).forGetter(OriginLayer::enabled),
        ComponentCodecHelper.CODEC.fieldOf("name").forGetter(OriginLayer::name),
        Codec.BOOL.optionalFieldOf("allow_random", false).forGetter(OriginLayer::allowRandom),
        ResourceLocation.CODEC.optionalFieldOf("default_origin").forGetter(OriginLayer::defaultOrigin),
        Codec.BOOL.optionalFieldOf("auto_choose", false).forGetter(OriginLayer::autoChoose),
        Codec.BOOL.optionalFieldOf("hidden", false).forGetter(OriginLayer::hidden),
        ResourceLocation.CODEC.listOf().optionalFieldOf("exclude_random", List.of()).forGetter(OriginLayer::excludeRandom)
    ).apply(inst, OriginLayer::new));

    /** False for an origin the layer lists under {@code exclude_random}: pickable, but never rolled. */
    public boolean isRandomCandidate(ResourceLocation origin) {
        return !excludeRandom.contains(origin);
    }

    public List<ResourceLocation> getAvailableOriginIds() {
        return origins.stream().map(ConditionedOrigin::origin).toList();
    }

    /** Returns origin IDs filtered by layer conditions against the player's current choices. */
    public List<ResourceLocation> getAvailableOriginIds(java.util.Map<ResourceLocation, ResourceLocation> chosenOrigins) {
        return origins.stream()
            .filter(co -> co.isAvailable(chosenOrigins))
            .map(ConditionedOrigin::origin)
            .toList();
    }
}
