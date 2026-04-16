package com.cyberday1.neoorigins.api.origin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * Codec-driven origin definition. Loaded from
 * {@code data/<namespace>/origins/origins/<id>.json} by {@code OriginDataManager}
 * and synced to clients verbatim via {@code SyncOriginRegistryPayload}.
 *
 * <p>The {@code furModel}, {@code furTexture} and {@code furAnimation} fields are optional
 * Phase 1 hooks for the Origin Furs feature — when present, the client renders a
 * GeckoLib {@code GeoModel} as a cosmetic overlay on the local player's vanilla body.
 * Missing fields are treated as "no fur" and existing origin JSONs deserialize unchanged.</p>
 */
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
    Optional<ResourceLocation> furModel,
    Optional<ResourceLocation> furTexture,
    Optional<ResourceLocation> furAnimation
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
        ResourceLocation.CODEC.optionalFieldOf("fur_model").forGetter(Origin::furModel),
        ResourceLocation.CODEC.optionalFieldOf("fur_texture").forGetter(Origin::furTexture),
        ResourceLocation.CODEC.optionalFieldOf("fur_animation").forGetter(Origin::furAnimation)
    ).apply(inst, Origin::new));
}
