package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class DamageInWaterPower extends PowerType<DamageInWaterPower.Config> {

    public record Config(
        float damagePerSecond,
        boolean includeRain,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("damage_per_second", 1.0f).forGetter(Config::damagePerSecond),
            Codec.BOOL.optionalFieldOf("include_rain", true).forGetter(Config::includeRain),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (player.isInWater()) {
            applyDamage(player, config);
            return;
        }
        if (config.includeRain() && player.level() instanceof ServerLevel level) {
            BlockPos headPos = player.blockPosition().above();
            if (level.isRaining() && level.canSeeSky(headPos)) {
                applyDamage(player, config);
            }
        }
    }

    private void applyDamage(ServerPlayer player, Config config) {
        if (player.tickCount % 20 == 0) {
            player.hurt(player.damageSources().magic(), config.damagePerSecond());
        }
    }
}
