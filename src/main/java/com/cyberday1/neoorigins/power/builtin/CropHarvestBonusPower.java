package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Grants bonus drops when the player breaks mature crops or logs.
 * The extra items match the block's own loot table drops.
 * Handled via BlockEvent.BreakEvent in OriginEventHandler.
 */
public class CropHarvestBonusPower extends PowerType<CropHarvestBonusPower.Config> {

    public record Config(int extraDrops, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("extra_drops", 1).forGetter(Config::extraDrops),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }
}
