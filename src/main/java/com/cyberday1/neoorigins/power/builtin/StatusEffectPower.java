package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

public class StatusEffectPower extends PowerType<StatusEffectPower.Config> {

    public record Config(
        Identifier effect,
        int amplifier,
        boolean ambient,
        boolean showParticles,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.fieldOf("effect").forGetter(Config::effect),
            Codec.INT.optionalFieldOf("amplifier", 0).forGetter(Config::amplifier),
            Codec.BOOL.optionalFieldOf("ambient", true).forGetter(Config::ambient),
            Codec.BOOL.optionalFieldOf("show_particles", false).forGetter(Config::showParticles),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        var effectHolderOpt = BuiltInRegistries.MOB_EFFECT.get(config.effect());
        if (effectHolderOpt.isEmpty()) return;
        var effectHolder = effectHolderOpt.get();
        // Only apply if not already active at the right level
        var existing = player.getEffect(effectHolder);
        if (existing == null || existing.getAmplifier() < config.amplifier()) {
            player.addEffect(new MobEffectInstance(
                effectHolder,
                300, // reapply every 15s (300 ticks), refreshed each tick
                config.amplifier(),
                config.ambient(),
                config.showParticles()
            ));
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        var effectHolderOpt = BuiltInRegistries.MOB_EFFECT.get(config.effect());
        effectHolderOpt.ifPresent(player::removeEffect);
    }
}
