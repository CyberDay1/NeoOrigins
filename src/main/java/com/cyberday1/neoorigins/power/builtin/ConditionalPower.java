package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public class ConditionalPower extends PowerType<ConditionalPower.Config> {

    public enum Condition {
        CLIMBING, IN_WATER, ON_GROUND, ALWAYS;

        public static final Codec<Condition> CODEC = Codec.STRING.xmap(
            s -> {
                try { return Condition.valueOf(s.toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException e) { return ALWAYS; }
            },
            c -> c.name().toLowerCase(Locale.ROOT)
        );
    }

    public record Config(
        Condition condition,
        Identifier innerPower,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Condition.CODEC.optionalFieldOf("condition", Condition.ALWAYS).forGetter(Config::condition),
            Identifier.CODEC.fieldOf("inner_power").forGetter(Config::innerPower),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    public boolean isConditionMet(ServerPlayer player, Config config) {
        return switch (config.condition()) {
            case CLIMBING -> player.onClimbable();
            case IN_WATER -> player.isInWater();
            case ON_GROUND -> player.onGround();
            case ALWAYS -> true;
        };
    }
}
