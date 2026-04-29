package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * Applies a post-processing shader to the player's view. Client-side
 * rendering is handled by {@code VisualEffectsHandler} which calls
 * {@code GameRenderer.loadEffect()} with the specified shader.
 *
 * <p>JSON fields:
 * <ul>
 *   <li>{@code shader} (string, required) — resource location of the shader
 *       (e.g. {@code "minecraft:spider"} loads {@code shaders/post/spider.json})</li>
 * </ul>
 *
 * <pre>{ "type": "neoorigins:shader", "shader": "minecraft:desaturate" }</pre>
 */
public class ShaderPower extends PowerType<ShaderPower.Config> {

    public record Config(String shader, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("shader").forGetter(Config::shader),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) {
        // Normalise Origins-style full paths:
        //   "minecraft:shaders/post/desaturate.json" → "minecraft:desaturate"
        String id = config.shader();
        if (id.contains("shaders/post/")) {
            id = id.replace("shaders/post/", "").replace(".json", "");
        }
        return Set.of("shader:" + id);
    }
}
