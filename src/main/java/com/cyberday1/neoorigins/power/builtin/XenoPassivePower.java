package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Set;

/**
 * Marks the player as carrying an alien presence. Emits the {@code "xeno_passive"}
 * capability tag, which {@code AvpAlienHostMixin} reads from inside the Aliens vs
 * Predator mod's {@code AlienPredicates.isHost} to short-circuit host eligibility —
 * facehuggers (and ovomorph hatch-desire logic) stop treating the player as a viable
 * host, as if they were already infected/immune.
 *
 * <p>Pure data holder — no server-side effect beyond the capability declaration. The
 * companion behaviour (xenomorphs ignoring the player entirely) is delivered separately
 * via a {@code mobs_ignore_player} power scoped to the {@code #avp_alien:aliens} /
 * {@code #avp_alien:parasites} tags, so this power only governs the facehugger host gate.
 *
 * <p>The mixin is a soft dependency: when AvP is not installed the capability simply
 * goes unread and this power is an inert marker.
 */
public class XenoPassivePower extends PowerType<XenoPassivePower.Config> {

    private static final Set<String> CAPS = Set.of("xeno_passive");

    public record Config(String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) { return CAPS; }
}
