package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Blocks vanilla food-based natural regeneration (the FoodData.tick heal
 * branches). Does NOT scale LivingHealEvent — potion Regeneration, beacon
 * regen, totem pops, and any data-pack `origins:heal` action still work.
 *
 * <p>Complements {@link NaturalRegenModifierPower}, which scales ALL
 * healing sources by a multiplier. Use this when an origin (e.g. Automaton
 * "Perpetual Engine") should feel "metabolism-less" — no passive healing
 * from food — while still allowing healing via explicit effects.
 *
 * <p>Implementation: `FoodDataNoRegenMixin` makes `Player.isHurt()` return
 * false from inside `FoodData.tick` when this power is active, so both
 * regen branches (saturation-based and hunger-based) skip.
 */
public class NoNaturalRegenPower extends PowerType<NoNaturalRegenPower.Config> {

    public record Config(String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }
}
