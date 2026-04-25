package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Set;

/**
 * Arachnid/spider affinity for cobwebs — emits the {@code cobweb_affinity}
 * capability tag, which gates:
 * <ul>
 *   <li>{@code EntityMakeStuckInBlockMixin} — skips the cobweb slowdown so
 *       the player moves through webs at normal speed</li>
 *   <li>{@code WorldPowerEvents.onBreakSpeed} — multiplies cobweb break
 *       speed so the player can mine webs in a tick or two</li>
 * </ul>
 */
public class CobwebAffinityPower extends PowerType<CobwebAffinityPower.Config> {

    public record Config(String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) {
        return Set.of("cobweb_affinity");
    }
}
