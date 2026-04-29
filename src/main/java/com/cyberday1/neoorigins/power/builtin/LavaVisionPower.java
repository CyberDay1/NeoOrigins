package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * Increases the player's vision distance while submerged in lava by
 * pushing back the lava fog far plane. Client-side rendering is handled
 * by {@code VisualEffectsHandler} which checks for the {@code lava_vision}
 * capability.
 *
 * <p>JSON fields:
 * <ul>
 *   <li>{@code strength} (float, default 3.0) — fog distance multiplier
 *       (higher = further vision in lava)</li>
 * </ul>
 *
 * <pre>{ "type": "neoorigins:lava_vision" }</pre>
 * <pre>{ "type": "neoorigins:lava_vision", "strength": 5.0 }</pre>
 */
public class LavaVisionPower extends PowerType<LavaVisionPower.Config> {

    public record Config(float strength, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("strength", 3.0f).forGetter(Config::strength),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) {
        return Set.of("lava_vision:" + config.strength());
    }
}
