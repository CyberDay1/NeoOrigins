package com.cyberday1.neoorigins.api.origin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

public record OriginUpgrade(
    Identifier advancement,
    Identifier origin,
    String announcement
) {
    public static final Codec<OriginUpgrade> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Identifier.CODEC.fieldOf("advancement").forGetter(OriginUpgrade::advancement),
        Identifier.CODEC.fieldOf("origin").forGetter(OriginUpgrade::origin),
        Codec.STRING.optionalFieldOf("announcement", "").forGetter(OriginUpgrade::announcement)
    ).apply(inst, OriginUpgrade::new));
}
