package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sets the player on fire at a configurable interval.
 *
 * <p>JSON fields:
 * <ul>
 *   <li>{@code interval} (int, default 20) — ticks between fire applications</li>
 *   <li>{@code burn_duration} (int, default 100) — ticks of fire per application (20 ticks = 1 second)</li>
 * </ul>
 *
 * <pre>{ "type": "neoorigins:burn", "interval": 20, "burn_duration": 100 }</pre>
 */
public class BurnPower extends PowerType<BurnPower.Config> {

    public record Config(int interval, int burnDuration, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("interval", 20).forGetter(Config::interval),
            Codec.INT.optionalFieldOf("burn_duration", 100).forGetter(Config::burnDuration),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (config.interval() <= 0) return;
        if (player.tickCount % config.interval() != 0) return;
        player.setRemainingFireTicks(config.burnDuration());
    }
}
