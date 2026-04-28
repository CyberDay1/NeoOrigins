package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Applies a status effect when the player is at or below a certain
 * light level. Removes the effect when they move to brighter light.
 *
 * <p>Use cases: Umbral invisibility in darkness, Darkness Mage shadow cloak.
 */
public class LightLevelEffectPower extends PowerType<LightLevelEffectPower.Config> {

    public record Config(
        int maxLightLevel,
        String effect,
        int amplifier,
        boolean ambient,
        boolean showParticles,
        boolean showIcon,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("max_light_level", 4).forGetter(Config::maxLightLevel),
            Codec.STRING.fieldOf("effect").forGetter(Config::effect),
            Codec.INT.optionalFieldOf("amplifier", 0).forGetter(Config::amplifier),
            Codec.BOOL.optionalFieldOf("ambient", true).forGetter(Config::ambient),
            Codec.BOOL.optionalFieldOf("show_particles", false).forGetter(Config::showParticles),
            Codec.BOOL.optionalFieldOf("show_icon", false).forGetter(Config::showIcon),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        int light = player.level().getMaxLocalRawBrightness(player.blockPosition());
        var effectHolder = BuiltInRegistries.MOB_EFFECT.get(
            Identifier.parse(config.effect())).orElse(null);
        if (effectHolder == null) return;

        if (light <= config.maxLightLevel()) {
            var existing = player.getEffect(effectHolder);
            if (existing == null || existing.getDuration() < 30) {
                player.addEffect(new MobEffectInstance(
                    effectHolder, 60, config.amplifier(),
                    config.ambient(), config.showParticles(), config.showIcon()));
            }
        } else {
            if (player.hasEffect(effectHolder)) {
                player.removeEffect(effectHolder);
            }
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        var effectHolder = BuiltInRegistries.MOB_EFFECT.get(
            Identifier.parse(config.effect())).orElse(null);
        if (effectHolder != null) player.removeEffect(effectHolder);
    }
}
