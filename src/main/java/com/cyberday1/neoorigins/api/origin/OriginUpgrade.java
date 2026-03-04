package com.cyberday1.neoorigins.api.origin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

public record OriginUpgrade(
    ResourceLocation advancement,
    ResourceLocation origin,
    String announcement
) {
    public static final Codec<OriginUpgrade> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        ResourceLocation.CODEC.fieldOf("advancement").forGetter(OriginUpgrade::advancement),
        ResourceLocation.CODEC.fieldOf("origin").forGetter(OriginUpgrade::origin),
        Codec.STRING.optionalFieldOf("announcement", "").forGetter(OriginUpgrade::announcement)
    ).apply(inst, OriginUpgrade::new));
}
