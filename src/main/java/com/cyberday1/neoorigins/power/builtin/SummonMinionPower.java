package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.service.MinionTracker;
import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Active power that summons a mob near the player. Summoned mobs are tracked
 * by MinionTracker with caps, despawn timers, and death-damage feedback.
 *
 * <p>Equipment can be configured per slot via JSON. All equipment drop chances
 * are set to 0 — summoned mobs never drop loot.
 */
public class SummonMinionPower extends AbstractActivePower<SummonMinionPower.Config> {

    public record Config(
        String mobType,
        int maxCount,
        int cooldownTicks,
        int hungerCost,
        int despawnTicks,
        float deathDamage,
        Optional<String> head,
        Optional<String> chest,
        Optional<String> legs,
        Optional<String> feet,
        Optional<String> mainhand,
        Optional<String> offhand,
        String type
    ) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("mob_type").forGetter(Config::mobType),
            Codec.INT.optionalFieldOf("max_count", 3).forGetter(Config::maxCount),
            Codec.INT.optionalFieldOf("cooldown_ticks", 200).forGetter(Config::cooldownTicks),
            Codec.INT.optionalFieldOf("hunger_cost", 4).forGetter(Config::hungerCost),
            Codec.INT.optionalFieldOf("despawn_ticks", 18000).forGetter(Config::despawnTicks),
            Codec.FLOAT.optionalFieldOf("death_damage", 1.0f).forGetter(Config::deathDamage),
            Codec.STRING.optionalFieldOf("head").forGetter(Config::head),
            Codec.STRING.optionalFieldOf("chest").forGetter(Config::chest),
            Codec.STRING.optionalFieldOf("legs").forGetter(Config::legs),
            Codec.STRING.optionalFieldOf("feet").forGetter(Config::feet),
            Codec.STRING.optionalFieldOf("mainhand").forGetter(Config::mainhand),
            Codec.STRING.optionalFieldOf("offhand").forGetter(Config::offhand),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public String getCooldownKey(Config config) {
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

        if (living instanceof Mob mob) {
            mob.setPersistenceRequired();

            // Apply configured equipment (or default helmet for sun protection)
            equipSlot(mob, EquipmentSlot.HEAD, config.head(), Items.IRON_HELMET.getDefaultInstance());
            equipSlot(mob, EquipmentSlot.CHEST, config.chest(), null);
            equipSlot(mob, EquipmentSlot.LEGS, config.legs(), null);
            equipSlot(mob, EquipmentSlot.FEET, config.feet(), null);
            equipSlot(mob, EquipmentSlot.MAINHAND, config.mainhand(), null);
            equipSlot(mob, EquipmentSlot.OFFHAND, config.offhand(), null);

            // Zero all drop chances — summoned mobs never drop loot
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                mob.setDropChance(slot, 0.0f);
            }
        }

        level.addFreshEntity(living);

        // Sound + particle effects at spawn location
        level.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z,
            SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 0.8f);
        level.sendParticles(ParticleTypes.SOUL,
            spawnPos.x, spawnPos.y + 0.5, spawnPos.z,
            20, 0.5, 0.5, 0.5, 0.02);
        level.sendParticles(ParticleTypes.SMOKE,
            spawnPos.x, spawnPos.y + 0.2, spawnPos.z,
            10, 0.3, 0.3, 0.3, 0.01);

        // Consume hunger
        player.getFoodData().setFoodLevel(player.getFoodData().getFoodLevel() - config.hungerCost());

        // Track the minion
        MinionTracker.track(player, living, config.mobType(),
            player.tickCount, config.despawnTicks(), config.deathDamage());

        return true;
    }

    private static void equipSlot(Mob mob, EquipmentSlot slot, Optional<String> configItem, ItemStack fallback) {
        if (configItem.isPresent()) {
            var itemOpt = BuiltInRegistries.ITEM.get(Identifier.parse(configItem.get()));
            if (itemOpt.isPresent()) {
                mob.setItemSlot(slot, new ItemStack(itemOpt.get().value()));
            }
        } else if (fallback != null && mob.getItemBySlot(slot).isEmpty()) {
            mob.setItemSlot(slot, fallback.copy());
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        MinionTracker.clearAll(player.getUUID());
    }
}
