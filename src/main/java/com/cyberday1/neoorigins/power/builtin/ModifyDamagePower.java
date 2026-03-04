package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public class ModifyDamagePower extends PowerType<ModifyDamagePower.Config> {

    public enum Direction { IN, OUT }

    public record Config(
        Direction direction,
        float multiplier,
        Optional<String> damageType,
        String type
    ) implements PowerConfiguration {

        private static final Codec<Direction> DIR_CODEC = Codec.STRING.xmap(
            s -> "out".equalsIgnoreCase(s) ? Direction.OUT : Direction.IN,
            d -> d == Direction.OUT ? "out" : "in"
        );

        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            DIR_CODEC.optionalFieldOf("direction", Direction.IN).forGetter(Config::direction),
            Codec.FLOAT.optionalFieldOf("multiplier", 1.0f).forGetter(Config::multiplier),
            Codec.STRING.optionalFieldOf("damage_type").forGetter(Config::damageType),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    // Event handling done in OriginEventHandler
    @Override public void onGranted(ServerPlayer player, Config config) {}
    @Override public void onRevoked(ServerPlayer player, Config config) {}
}
