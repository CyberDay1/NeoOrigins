package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public class TickActionPower extends PowerType<TickActionPower.Config> {

    public enum ActionType {
        TELEPORT_ON_DAMAGE, NONE;

        public static final Codec<ActionType> CODEC = Codec.STRING.xmap(
            s -> {
                try { return ActionType.valueOf(s.toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException e) { return NONE; }
            },
            a -> a.name().toLowerCase(Locale.ROOT)
        );
    }

    public record Config(int interval, ActionType actionType, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("interval", 20).forGetter(Config::interval),
            ActionType.CODEC.optionalFieldOf("action_type", ActionType.NONE).forGetter(Config::actionType),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (player.tickCount % config.interval() != 0) return;
        // Action dispatched by OriginEventHandler based on actionType
    }
}
