package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;

/**
 * Tints the player model with an RGBA colour. Client-side rendering is
 * handled by {@code VisualEffectsHandler} which reads the encoded colour
 * from the capability tag.
 *
 * <p>JSON fields:
 * <ul>
 *   <li>{@code red}   (float, default 1.0) — red channel 0.0–1.0</li>
 *   <li>{@code green} (float, default 1.0) — green channel 0.0–1.0</li>
 *   <li>{@code blue}  (float, default 1.0) — blue channel 0.0–1.0</li>
 *   <li>{@code alpha} (float, default 1.0) — alpha channel 0.0–1.0</li>
 * </ul>
 *
 * <pre>{ "type": "neoorigins:model_color", "red": 0.5, "green": 1.0, "blue": 0.5, "alpha": 0.8 }</pre>
 */
public class ModelColorPower extends PowerType<ModelColorPower.Config> {

    private static final java.util.Map<java.util.UUID, Boolean> CONDITION_STATE = new java.util.concurrent.ConcurrentHashMap<>();

    public record Config(float red, float green, float blue, float alpha, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("red",   1.0f).forGetter(Config::red),
            Codec.FLOAT.optionalFieldOf("green", 1.0f).forGetter(Config::green),
            Codec.FLOAT.optionalFieldOf("blue",  1.0f).forGetter(Config::blue),
            Codec.FLOAT.optionalFieldOf("alpha", 1.0f).forGetter(Config::alpha),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) {
        return Set.of("model_color:" + config.red() + ":" + config.green()
            + ":" + config.blue() + ":" + config.alpha());
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        CONDITION_STATE.remove(player.getUUID());
    }
}
