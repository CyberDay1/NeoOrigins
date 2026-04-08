package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.service.MinionTracker;
import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Active power that summons a mob near the player. Summoned mobs are tracked
 * by MinionTracker with caps, despawn timers, and death-damage feedback.
 */
public class SummonMinionPower extends AbstractActivePower<SummonMinionPower.Config> {

    public record Config(
        String mobType,
        int maxCount,
        int cooldownTicks,
        int hungerCost,
        int despawnTicks,
        float deathDamage,
        String type
    ) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("mob_type").forGetter(Config::mobType),
            Codec.INT.optionalFieldOf("max_count", 3).forGetter(Config::maxCount),
            Codec.INT.optionalFieldOf("cooldown_ticks", 200).forGetter(Config::cooldownTicks),
            Codec.INT.optionalFieldOf("hunger_cost", 4).forGetter(Config::hungerCost),
            Codec.INT.optionalFieldOf("despawn_ticks", 18000).forGetter(Config::despawnTicks),
            Codec.FLOAT.optionalFieldOf("death_damage", 1.0f).forGetter(Config::deathDamage),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected String getCooldownKey(Config config) {
        return getClass().getName() + ":" + config.mobType();
    }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        // Check cap
        int alive = MinionTracker.countAlive(player.getUUID(), config.mobType());
        if (alive >= config.maxCount()) return false;

        // Check hunger
        if (player.getFoodData().getFoodLevel() < config.hungerCost()) return false;

        // Resolve entity type
        var entityTypeOpt = BuiltInRegistries.ENTITY_TYPE.get(Identifier.parse(config.mobType()));
        if (entityTypeOpt.isEmpty()) return false;
        EntityType<?> entityType = entityTypeOpt.get().value();

        // Spawn the minion near the player
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();
        Vec3 spawnPos = player.position().add(look.x * 2, 0, look.z * 2);

        Entity entity = entityType.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (!(entity instanceof LivingEntity living)) return false;

        living.setPos(spawnPos.x, spawnPos.y, spawnPos.z);

        // Make the mob not attack the summoner and target what they target
        if (living instanceof Mob mob) {
            mob.setPersistenceRequired();
        }

        level.addFreshEntity(living);

        // Consume hunger
        player.getFoodData().setFoodLevel(player.getFoodData().getFoodLevel() - config.hungerCost());

        // Track the minion
        MinionTracker.track(player, living, config.mobType(),
            player.tickCount, config.despawnTicks(), config.deathDamage());

        return true;
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        // Clean up minions when the power is revoked
        MinionTracker.clearAll(player.getUUID());
    }
}
