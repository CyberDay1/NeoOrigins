package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Set;

/**
 * Hides an HUD bar for origins that don't need it. Emits a capability tag
 * that client-side Gui mixins check before rendering the matching bar.
 *
 * <p>JSON shape:
 * <pre>{@code
 * { "type": "neoorigins:hide_hud_bar", "bar": "hunger" }
 * { "type": "neoorigins:hide_hud_bar", "bar": "air"    }
 * }</pre>
 *
 * <p>Pack authors attach these to origins whose other powers make the bar
 * redundant — Automaton's {@code multiplier: 0.0} hunger drain and Automaton /
 * Merling / Kraken's {@code water_breathing} respectively. The hide is a pure
 * visual change; the underlying mechanic (hunger freeze, air preservation) is
 * driven by the power that actually zeros the drain.
 */
public class HideHudBarPower extends PowerType<HideHudBarPower.Config> {

    public record Config(String bar, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("bar", "hunger").forGetter(Config::bar),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) {
        return switch (config.bar().toLowerCase()) {
            case "hunger", "food" -> Set.of("hide_hunger_bar");
            case "air", "oxygen", "breath" -> Set.of("hide_air_bar");
            default -> Set.of();
        };
    }
}
