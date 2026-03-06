package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ignites the player when their HP drops below a percentage of max health.
 * Useful for berserker-style rage powers or unstable construct themes.
 */
public class BurnAtHealthThresholdPower extends PowerType<BurnAtHealthThresholdPower.Config> {

    public record Config(float thresholdPercent, int fireTicks, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("threshold_percent", 0.25f).forGetter(Config::thresholdPercent),
            Codec.INT.optionalFieldOf("fire_ticks", 60).forGetter(Config::fireTicks),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        float threshold = player.getMaxHealth() * config.thresholdPercent();
        if (player.getHealth() <= threshold && !player.isOnFire()) {
            player.setRemainingFireTicks(config.fireTicks());
        }
    }
}
