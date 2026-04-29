package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * Renders a full-screen texture overlay on the player's HUD. Client-side only —
 * the server emits a capability tag containing the texture path and strength;
 * {@code VisualEffectsHandler} on the client reads it and renders the overlay.
 *
 * <p>JSON fields:
 * <ul>
 *   <li>{@code texture} (string, required) — resource location of the overlay texture</li>
 *   <li>{@code strength} (float, default 1.0) — overlay opacity (0.0 = invisible, 1.0 = opaque)</li>
 * </ul>
 *
 * <pre>{ "type": "neoorigins:overlay", "texture": "minecraft:textures/misc/pumpkinblur.png", "strength": 0.5 }</pre>
 */
public class OverlayPower extends PowerType<OverlayPower.Config> {

    public record Config(String texture, float strength, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("texture").forGetter(Config::texture),
            Codec.FLOAT.optionalFieldOf("strength", 1.0f).forGetter(Config::strength),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) {
        return Set.of("overlay:" + config.texture() + ":" + config.strength());
    }
}
