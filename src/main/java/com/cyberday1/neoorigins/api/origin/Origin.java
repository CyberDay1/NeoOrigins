package com.cyberday1.neoorigins.api.origin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

public record Origin(
    Identifier id,
    List<Identifier> powers,
    Identifier icon,
    Impact impact,
    int order,
    boolean unchoosable,
    boolean special,
    Component name,
    Component description,
    List<OriginUpgrade> upgrades
) {
    public static final Codec<Origin> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Identifier.CODEC.fieldOf("id").forGetter(Origin::id),
        Identifier.CODEC.listOf().optionalFieldOf("powers", List.of()).forGetter(Origin::powers),
        Identifier.CODEC.optionalFieldOf("icon", Identifier.fromNamespaceAndPath("minecraft", "stone")).forGetter(Origin::icon),
        Impact.CODEC.optionalFieldOf("impact", Impact.NONE).forGetter(Origin::impact),
        Codec.INT.optionalFieldOf("order", 0).forGetter(Origin::order),
        Codec.BOOL.optionalFieldOf("unchoosable", false).forGetter(Origin::unchoosable),
        Codec.BOOL.optionalFieldOf("special", false).forGetter(Origin::special),
        ComponentCodecHelper.CODEC.fieldOf("name").forGetter(Origin::name),
        ComponentCodecHelper.CODEC.fieldOf("description").forGetter(Origin::description),
        OriginUpgrade.CODEC.listOf().optionalFieldOf("upgrades", List.of()).forGetter(Origin::upgrades)
    ).apply(inst, Origin::new));
}
