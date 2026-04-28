package com.cyberday1.neoorigins.api.origin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Defines power modifications for an evolution tier. At a given tier,
 * powers in {@code add} are granted and powers in {@code remove} are
 * revoked (they won't appear in the player's active power set).
 *
 * <p>Stacks cumulatively: tier 2 includes tier 1's changes plus its own.
 *
 * <p>Example JSON:
 * <pre>{@code
 * "tier_powers": [
 *   { "tier": 1, "add": ["neoorigins:wraith_night_vision"], "remove": [] },
 *   { "tier": 2, "add": ["neoorigins:wraith_weakness_aura"], "remove": [] },
 *   { "tier": 3, "add": ["neoorigins:wraith_apex_phase"], "remove": ["neoorigins:wraith_phase"] }
 * ]
 * }</pre>
 */
public record OriginTierOverlay(
    int tier,
    List<Identifier> add,
    List<Identifier> remove
) {
    public static final Codec<OriginTierOverlay> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.INT.fieldOf("tier").forGetter(OriginTierOverlay::tier),
        Identifier.CODEC.listOf().optionalFieldOf("add", List.of()).forGetter(OriginTierOverlay::add),
        Identifier.CODEC.listOf().optionalFieldOf("remove", List.of()).forGetter(OriginTierOverlay::remove)
    ).apply(inst, OriginTierOverlay::new));
}
