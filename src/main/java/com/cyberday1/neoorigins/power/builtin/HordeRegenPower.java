package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.service.MinionTracker;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Passive power that slowly regenerates health for all tamed mobs tracked
 * via MinionTracker. Healing only applies when the mob is not in combat
 * (no recent damage taken).
 */
public class HordeRegenPower extends PowerType<HordeRegenPower.Config> {

    public record Config(
        float healAmount,
        int intervalTicks,
        int combatCooldownTicks,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("heal_amount", 1.0f).forGetter(Config::healAmount),
            Codec.INT.optionalFieldOf("interval_ticks", 120).forGetter(Config::intervalTicks),
            Codec.INT.optionalFieldOf("combat_cooldown_ticks", 100).forGetter(Config::combatCooldownTicks),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (config.intervalTicks() <= 0 || player.tickCount % config.intervalTicks() != 0) return;

        var tamed = MinionTracker.getAlive(player.getUUID(), TameMobPower.tamedMobKey());
        for (var minion : tamed) {
            LivingEntity entity = minion.entity();
            if (!entity.isAlive()) continue;
            if (entity.getHealth() >= entity.getMaxHealth()) continue;

            // Skip if mob was hurt by another mob recently (in combat).
            // Uses getLastHurtByMobTimestamp() exclusively — getLastDamageSource()
            // tracks any damage (fall, fire, etc.) and conflates the two systems.
            if (entity.getLastHurtByMobTimestamp() > 0
                    && entity.tickCount - entity.getLastHurtByMobTimestamp() < config.combatCooldownTicks()) {
                continue;
            }

            entity.heal(config.healAmount());
        }
    }
}
