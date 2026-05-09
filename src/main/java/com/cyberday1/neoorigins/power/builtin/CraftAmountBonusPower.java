package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Grants bonus items when crafting specific outputs (e.g., more planks from logs).
 * Event-driven via {@code PlayerEvent.ItemCraftedEvent} — handled in
 * {@link com.cyberday1.neoorigins.event.CraftingPowerEvents#onItemCrafted}.
 */
public class CraftAmountBonusPower extends PowerType<CraftAmountBonusPower.Config> {

    public record Config(String outputItem, int bonusCount, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("output_item", "minecraft:oak_planks").forGetter(Config::outputItem),
            Codec.INT.optionalFieldOf("bonus_count", 4).forGetter(Config::bonusCount),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }
}
