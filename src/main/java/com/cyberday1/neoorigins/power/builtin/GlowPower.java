package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class GlowPower extends PowerType<GlowPower.Config> {

    public record Config(String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        var existing = player.getEffect(MobEffects.GLOWING);
        if (existing == null || existing.getDuration() < 210) {
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0, true, false));
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        player.removeEffect(MobEffects.GLOWING);
    }
}
