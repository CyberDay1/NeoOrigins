package com.cyberday1.neoorigins.api.origin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

public record OriginLayer(
    Identifier id,
    int order,
    List<ConditionedOrigin> origins,
    boolean enabled,
    Component name,
    boolean allowRandom,
    Optional<Identifier> defaultOrigin,
    boolean autoChoose,
    boolean hidden
) {
    public static final Codec<OriginLayer> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Identifier.CODEC.fieldOf("id").forGetter(OriginLayer::id),
        Codec.INT.optionalFieldOf("order", 0).forGetter(OriginLayer::order),
        ConditionedOrigin.CODEC.listOf().optionalFieldOf("origins", List.of()).forGetter(OriginLayer::origins),
        Codec.BOOL.optionalFieldOf("enabled", true).forGetter(OriginLayer::enabled),
        ComponentCodecHelper.CODEC.fieldOf("name").forGetter(OriginLayer::name),
        Codec.BOOL.optionalFieldOf("allow_random", false).forGetter(OriginLayer::allowRandom),
        Identifier.CODEC.optionalFieldOf("default_origin").forGetter(OriginLayer::defaultOrigin),
        Codec.BOOL.optionalFieldOf("auto_choose", false).forGetter(OriginLayer::autoChoose),
        Codec.BOOL.optionalFieldOf("hidden", false).forGetter(OriginLayer::hidden)
    ).apply(inst, OriginLayer::new));

    public List<Identifier> getAvailableOriginIds() {
        return origins.stream().map(ConditionedOrigin::origin).toList();
    }
}
