package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import java.util.Set;

/**
 * Increases the player's vision distance while the camera is submerged in
 * lava by pushing back the lava fog planes, and suppresses the first-person
 * burning-screen fire overlay. Client-side rendering is handled
 * by {@code VisualEffectsHandler} which checks for the {@code lava_vision}
 * capability.
 *
 * <p>The fog planes can be set either relatively or absolutely:
 * <ul>
 *   <li>{@code strength} (float, default 3.0) — multiplier applied to
 *       vanilla's lava fog planes. Note that vanilla's own values depend on
 *       fire resistance (end is 1.0 without it, 5.0 with it), so the same
 *       multiplier yields different distances depending on status effects.</li>
 *   <li>{@code start} (float, optional) — absolute fog start, in blocks.</li>
 *   <li>{@code end} (float, optional) — absolute fog end, in blocks. This is
 *       effectively "how far you can see through lava".</li>
 * </ul>
 *
 * <p>An absolute field always wins over {@code strength} for the plane it
 * names; a plane with no absolute value falls back to the multiplier. These
 * mirror the {@code s} and {@code v} fields of Origins' {@code lava_vision},
 * which are absolute replacements for vanilla's fog constants.
 *
 * <pre>{ "type": "neoorigins:lava_vision" }</pre>
 * <pre>{ "type": "neoorigins:lava_vision", "strength": 5.0 }</pre>
 * <pre>{ "type": "neoorigins:lava_vision", "start": 0.0, "end": 15.0 }</pre>
 */
public class LavaVisionPower extends PowerType<LavaVisionPower.Config> {

    /** Capability prefix; the payload is {@code strength:start:end}. */
    public static final String CAPABILITY_PREFIX = "lava_vision:";

    public record Config(float strength, Optional<Float> start, Optional<Float> end, String type)
        implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("strength", 3.0f).forGetter(Config::strength),
            Codec.FLOAT.optionalFieldOf("start").forGetter(Config::start),
            Codec.FLOAT.optionalFieldOf("end").forGetter(Config::end),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    /**
     * Encodes all three values in a fixed arity so the client can tell an
     * absent absolute plane from a supplied one. {@link Float#NaN} marks
     * "not set" and survives {@code toString}/{@code parseFloat} exactly.
     */
    @Override
    public Set<String> capabilities(Config config) {
        return Set.of(CAPABILITY_PREFIX
            + config.strength()
            + ':' + config.start().orElse(Float.NaN)
            + ':' + config.end().orElse(Float.NaN));
    }
}
