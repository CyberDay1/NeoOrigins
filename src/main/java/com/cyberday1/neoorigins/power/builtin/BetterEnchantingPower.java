package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Adds bonus levels to enchanting table offers.
 * Handled via EnchantmentLevelSetEvent in CraftingPowerEvents.
 */
public class BetterEnchantingPower extends PowerType<BetterEnchantingPower.Config> {

    public record Config(int bonusLevels, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("bonus_levels", 5).forGetter(Config::bonusLevels),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }
}
