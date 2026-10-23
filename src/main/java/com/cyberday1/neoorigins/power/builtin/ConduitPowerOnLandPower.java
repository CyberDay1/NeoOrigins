package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Set;

/**
 * An active conduit grants the holder Conduit Power on dry land too, not only
 * while they are in water or rain. Origins' {@code origins:conduit_power_on_land}
 * (the Merling's hidden power): it does not grant the effect by itself, it only
 * lifts vanilla's water-or-rain requirement, so a conduit still has to be built
 * and active nearby. Read by {@code ConduitBlockEntityMixin}.
 */
public class ConduitPowerOnLandPower extends PowerType<ConduitPowerOnLandPower.Config> {

    public static final String CAPABILITY = "conduit_power_on_land";

    public record Config(String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) {
        return Set.of(CAPABILITY);
    }
}
