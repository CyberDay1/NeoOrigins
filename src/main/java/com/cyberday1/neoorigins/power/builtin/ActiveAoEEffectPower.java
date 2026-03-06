package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Active ability that applies a mob effect to all nearby living entities within a radius.
 * Useful for "root" / area-of-effect crowd control.
 */
public class ActiveAoEEffectPower extends AbstractActivePower<ActiveAoEEffectPower.Config> {

    public record Config(
        String effect,
        int amplifier,
        int durationTicks,
        double radius,
        int cooldownTicks,
        String type
    ) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("effect", "minecraft:slowness").forGetter(Config::effect),
            Codec.INT.optionalFieldOf("amplifier", 4).forGetter(Config::amplifier),
            Codec.INT.optionalFieldOf("duration_ticks", 60).forGetter(Config::durationTicks),
            Codec.DOUBLE.optionalFieldOf("radius", 6.0).forGetter(Config::radius),
            Codec.INT.optionalFieldOf("cooldown_ticks", 200).forGetter(Config::cooldownTicks),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        var effectOpt = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(config.effect()));
        if (effectOpt.isEmpty()) return false;
        ServerLevel level = (ServerLevel) player.level();
        AABB box = player.getBoundingBox().inflate(config.radius());
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player);
        var effectHolder = effectOpt.get();
        for (LivingEntity target : targets)
            target.addEffect(new MobEffectInstance(effectHolder, config.durationTicks(), config.amplifier()));
        return true;
    }
}
