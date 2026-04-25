package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.condition.LocationCondition;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Overrides the player's respawn location to a power-configured target. Unlike
 * the origin's {@code spawn_location} (which only fires on first-join and on
 * bed-less respawn), this power fires on every respawn. Optionally also
 * overrides the bed/respawn-anchor spawn point.
 */
public class ModifyPlayerSpawnPower extends PowerType<ModifyPlayerSpawnPower.Config> {

    public record Config(LocationCondition location, boolean overrideBed, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            LocationCondition.CODEC.fieldOf("location").forGetter(Config::location),
            Codec.BOOL.optionalFieldOf("override_bed", false).forGetter(Config::overrideBed),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }
}
