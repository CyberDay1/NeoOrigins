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
 *   <li>{@code MovementPowerEvents.onBreakSpeed} — multiplies cobweb break
 *       speed so the player can mine webs in a tick or two</li>
 * </ul>
 *
 * <p>With {@code climb} on it also emits {@code web_climb}, which
 * {@code LivingEntityClimbMixin} reads to treat any {@code #neoorigins:cobwebs}
 * block the player overlaps as a ladder: jump to climb, sneak to hold. That is
 * the climbing half of Origins' Arachnid {@code master_of_webs}.
 */
public class CobwebAffinityPower extends PowerType<CobwebAffinityPower.Config> {

    public static final String WEB_CLIMB = "web_climb";

    public record Config(boolean climb, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.BOOL.optionalFieldOf("climb", false).forGetter(Config::climb),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) {
        return config.climb() ? Set.of("cobweb_affinity", WEB_CLIMB) : Set.of("cobweb_affinity");
    }
}
