package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/**
 * Triggers an action each time the player kills a living entity.
 * action values: "restore_health", "restore_hunger", "grant_effect"
 */
public class ActionOnKillPower extends PowerType<ActionOnKillPower.Config> {

    public record Config(
        String action,
        float amount,
        Optional<ResourceLocation> effect,
        int duration,
        int amplifier,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("action", "restore_health").forGetter(Config::action),
            Codec.FLOAT.optionalFieldOf("amount", 4.0f).forGetter(Config::amount),
            ResourceLocation.CODEC.optionalFieldOf("effect").forGetter(Config::effect),
            Codec.INT.optionalFieldOf("duration", 200).forGetter(Config::duration),
            Codec.INT.optionalFieldOf("amplifier", 0).forGetter(Config::amplifier),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onKill(ServerPlayer player, Config config, LivingEntity killed) {
        switch (config.action()) {
            case "restore_health" -> player.heal(config.amount());
            case "restore_hunger" -> player.getFoodData().eat((int) config.amount(), 0);
            case "grant_effect" -> {
                if (config.effect().isEmpty()) return;
                BuiltInRegistries.MOB_EFFECT.getOptional(config.effect().get()).ifPresent(effect -> {
                    var holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
                    player.addEffect(new MobEffectInstance(holder, config.duration(), config.amplifier(), false, true));
                });
            }
        }
    }
}
