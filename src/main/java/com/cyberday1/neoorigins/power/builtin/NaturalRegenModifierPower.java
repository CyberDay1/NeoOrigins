package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Scales all healing received by this player (via LivingHealEvent).
 * 0.0 = no healing at all, 1.0 = vanilla, 3.0 = triple regen.
 * Note: affects all healing sources (potions, regen effect, natural saturation regen).
 * Event handling via LivingHealEvent in OriginEventHandler.
 */
public class NaturalRegenModifierPower extends PowerType<NaturalRegenModifierPower.Config> {

    public record Config(float multiplier, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("multiplier", 1.0f).forGetter(Config::multiplier),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }
}
