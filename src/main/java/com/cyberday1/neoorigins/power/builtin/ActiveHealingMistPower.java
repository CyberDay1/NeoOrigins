package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * AoE healing burst — heals the caster and nearby players/allies.
 */
public class ActiveHealingMistPower extends AbstractActivePower<ActiveHealingMistPower.Config> {

    public record Config(
        float healAmount,
        double radius,
        boolean healSelf,
        int cooldownTicks,
        String type
    ) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("heal_amount", 6.0f).forGetter(Config::healAmount),
            Codec.DOUBLE.optionalFieldOf("radius", 8.0).forGetter(Config::radius),
            Codec.BOOL.optionalFieldOf("heal_self", true).forGetter(Config::healSelf),
            Codec.INT.optionalFieldOf("cooldown_ticks", 200).forGetter(Config::cooldownTicks),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 center = player.position();

        int healed = 0;

        // Heal self
        if (config.healSelf() && player.getHealth() < player.getMaxHealth()) {
            player.heal(config.healAmount());
            healed++;
        }

        // Heal nearby players
        AABB box = player.getBoundingBox().inflate(config.radius());
        var targets = level.getEntitiesOfClass(Player.class, box, e -> e != player && e.isAlive());
        for (Player target : targets) {
            if (target.getHealth() < target.getMaxHealth()) {
                target.heal(config.healAmount());
                healed++;
            }
        }

        // Effects
        level.playSound(null, center.x, center.y, center.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.5f);
        level.sendParticles(ParticleTypes.HEART,
            center.x, center.y + 1.0, center.z,
            10, config.radius() * 0.3, 0.5, config.radius() * 0.3, 0.02);
        level.sendParticles(ParticleTypes.SPLASH,
            center.x, center.y + 0.5, center.z,
            30, config.radius() * 0.4, 0.3, config.radius() * 0.4, 0.05);

        return healed > 0;
    }
}
