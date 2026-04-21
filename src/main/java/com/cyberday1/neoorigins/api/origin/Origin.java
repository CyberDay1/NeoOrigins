package com.cyberday1.neoorigins.api.origin;

import com.cyberday1.neoorigins.api.condition.LocationCondition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

public record Origin(
    ResourceLocation id,
    List<ResourceLocation> powers,
    ResourceLocation icon,
    Impact impact,
    int order,
    boolean unchoosable,
    boolean special,
    Component name,
    Component description,
    List<OriginUpgrade> upgrades,
    Optional<LocationCondition> spawnLocation
) {
    public static final Codec<Origin> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        ResourceLocation.CODEC.fieldOf("id").forGetter(Origin::id),
        ResourceLocation.CODEC.listOf().optionalFieldOf("powers", List.of()).forGetter(Origin::powers),
        ResourceLocation.CODEC.optionalFieldOf("icon", ResourceLocation.fromNamespaceAndPath("minecraft", "stone")).forGetter(Origin::icon),
        Impact.CODEC.optionalFieldOf("impact", Impact.NONE).forGetter(Origin::impact),
        Codec.INT.optionalFieldOf("order", 0).forGetter(Origin::order),
        Codec.BOOL.optionalFieldOf("unchoosable", false).forGetter(Origin::unchoosable),
        Codec.BOOL.optionalFieldOf("special", false).forGetter(Origin::special),
        ComponentCodecHelper.CODEC.fieldOf("name").forGetter(Origin::name),
        ComponentCodecHelper.CODEC.fieldOf("description").forGetter(Origin::description),
        OriginUpgrade.CODEC.listOf().optionalFieldOf("upgrades", List.of()).forGetter(Origin::upgrades),
        LocationCondition.CODEC.optionalFieldOf("spawn_location").forGetter(Origin::spawnLocation)
    ).apply(inst, Origin::new));
}
