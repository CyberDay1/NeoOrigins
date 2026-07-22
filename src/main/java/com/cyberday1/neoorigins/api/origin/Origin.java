package com.cyberday1.neoorigins.api.origin;

import com.cyberday1.neoorigins.api.condition.LocationCondition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public record Origin(
    Identifier id,
    List<Identifier> powers,
    ItemStack icon,
    Impact impact,
    int order,
    boolean unchoosable,
    boolean special,
    Component name,
    Component description,
    List<OriginUpgrade> upgrades,
    Optional<LocationCondition> spawnLocation,
    List<OriginTierOverlay> tierPowers,
    Optional<String> figuraModel,
    Optional<FiguraModelMap> figuraModels
) {
    public static final Codec<Origin> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Identifier.CODEC.fieldOf("id").forGetter(Origin::id),
        Identifier.CODEC.listOf().optionalFieldOf("powers", List.of()).forGetter(Origin::powers),
        IconCodec.CODEC.optionalFieldOf("icon", ItemStack.EMPTY).forGetter(Origin::icon),
        Impact.CODEC.optionalFieldOf("impact", Impact.NONE).forGetter(Origin::impact),
        Codec.INT.optionalFieldOf("order", 0).forGetter(Origin::order),
        Codec.BOOL.optionalFieldOf("unchoosable", false).forGetter(Origin::unchoosable),
        Codec.BOOL.optionalFieldOf("special", false).forGetter(Origin::special),
        ComponentCodecHelper.CODEC.fieldOf("name").forGetter(Origin::name),
        ComponentCodecHelper.CODEC.fieldOf("description").forGetter(Origin::description),
        OriginUpgrade.CODEC.listOf().optionalFieldOf("upgrades", List.of()).forGetter(Origin::upgrades),
        LocationCondition.CODEC.optionalFieldOf("spawn_location").forGetter(Origin::spawnLocation),
        OriginTierOverlay.CODEC.listOf().optionalFieldOf("tier_powers", List.of()).forGetter(Origin::tierPowers),
        // Optional opaque key naming the Figura model that represents this origin.
        // Surfaced to the Figura Lua sandbox (soft-dep) via NeoOriginsFiguraGlobal;
        // no validation, absent by default so every existing origin loads unchanged.
        Codec.STRING.optionalFieldOf("figura_model").forGetter(Origin::figuraModel),
        // Optional advanced reactive Figura-model maps (tiers / powers / capabilities
        // / vocab). Also opaque and soft-dep only; absent by default. See FiguraModelMap.
        FiguraModelMap.CODEC.optionalFieldOf("figura_models").forGetter(Origin::figuraModels)
    ).apply(inst, Origin::new));

    /**
     * Returns the effective power list for the given evolution tier.
     * Tier 0 = base powers. Higher tiers cumulatively apply all overlays
     * up to that tier (add/remove).
     */
    public List<Identifier> powersForTier(int tier) {
        if (tier <= 0 || tierPowers.isEmpty()) return powers;
        java.util.LinkedHashSet<Identifier> effective = new java.util.LinkedHashSet<>(powers);
        for (OriginTierOverlay overlay : tierPowers) {
            if (overlay.tier() <= tier) {
                overlay.remove().forEach(effective::remove);
                effective.addAll(overlay.add());
            }
        }
        return List.copyOf(effective);
    }
}
