package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A bare, stateless boolean power. The power is just a data-holder — its state
 * lives in {@code CompatAttachments.toggleState()} keyed by the power's
 * registered id (e.g. {@code mypack:my_toggle}).
 *
 * <p>Read the current value with {@code origins:power_active { power: "mypack:my_toggle" }}
 * and flip it with {@code neoorigins:toggle { power: "mypack:my_toggle" }}.
 *
 * <p>Unlike {@code active_ability}, TogglePower does not consume a keybind slot.
 * It is purely a named boolean other powers gate on. To install an initial state,
 * wire up an {@code action_on_event} on {@code GAINED} that calls
 * {@code neoorigins:toggle} with an explicit {@code value}.
 */
public class TogglePower extends PowerType<TogglePower.Config> {

    public record Config(boolean defaultValue, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.BOOL.optionalFieldOf("default", false).forGetter(Config::defaultValue),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }
}
