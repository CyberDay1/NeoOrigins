package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Multiplies the player's mining speed when breaking blocks matching a block tag.
 * multiplier > 1 = faster, < 1 = slower.
 * Use block_tag = "minecraft:stone" or any custom tag.
 * Event handling via PlayerEvent.BreakSpeed in OriginEventHandler.
 */
public class BreakSpeedModifierPower extends PowerType<BreakSpeedModifierPower.Config> {

    public record Config(Identifier blockTag, float multiplier, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.fieldOf("block_tag").forGetter(Config::blockTag),
            Codec.FLOAT.optionalFieldOf("multiplier", 2.0f).forGetter(Config::multiplier),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override public void onGranted(ServerPlayer player, Config config) {}
    @Override public void onRevoked(ServerPlayer player, Config config) {}
}
