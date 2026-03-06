package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LightLayer;

public class DamageInDaylightPower extends PowerType<DamageInDaylightPower.Config> {

    public record Config(
        float damagePerSecond,
        boolean ignite,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("damage_per_second", 1.0f).forGetter(Config::damagePerSecond),
            Codec.BOOL.optionalFieldOf("ignite", false).forGetter(Config::ignite),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (!(player.level() instanceof ServerLevel level)) return;
        BlockPos headPos = player.blockPosition().above();

        boolean exposedToSky = level.getBrightness(LightLayer.SKY, headPos) >= 15
                && level.canSeeSky(headPos)
                && !level.isRaining()
                && level.getDayTime() % 24000L < 13000L;

        if (!exposedToSky || player.isInWater() || player.isOnFire()) return;

        if (config.ignite()) {
            player.setRemainingFireTicks(40);
        } else {
            // Apply damage_per_second spread over 20 ticks
            if (player.tickCount % 20 == 0) {
                player.hurt(player.damageSources().inFire(), config.damagePerSecond());
            }
        }
    }
}
