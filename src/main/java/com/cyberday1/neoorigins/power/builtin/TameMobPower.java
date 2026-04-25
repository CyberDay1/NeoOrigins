package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.service.MinionTracker;
import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Active power that tames a hostile mob the player is looking at.
 * The mob's AI is rewritten to follow the player and defend them.
 * Tamed mobs are tracked via MinionTracker.
 */
public class TameMobPower extends AbstractActivePower<TameMobPower.Config> {

    private static final String TAMED_MOB_KEY = "tamer:tamed";

    public record Config(
        double range,
        int maxTamed,
        int cooldownTicks,
        int hungerCost,
        int despawnTicks,
        float deathDamage,
        String type
    ) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.DOUBLE.optionalFieldOf("range", 16.0).forGetter(Config::range),
            Codec.INT.optionalFieldOf("max_tamed", 4).forGetter(Config::maxTamed),
            Codec.INT.optionalFieldOf("cooldown_ticks", 200).forGetter(Config::cooldownTicks),
            Codec.INT.optionalFieldOf("hunger_cost", 3).forGetter(Config::hungerCost),
            Codec.INT.optionalFieldOf("despawn_ticks", 36000).forGetter(Config::despawnTicks),
            Codec.FLOAT.optionalFieldOf("death_damage", 0.5f).forGetter(Config::deathDamage),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        // Check cap
        int alive = MinionTracker.countAlive(player.getUUID(), TAMED_MOB_KEY);
        if (alive >= config.maxTamed()) {
            player.sendSystemMessage(Component.translatable(
                "power.neoorigins.tame_mob.max_reached").withStyle(ChatFormatting.RED), true);
            return false;
        }

        // Check hunger
        if (player.getFoodData().getFoodLevel() < config.hungerCost()) {
            player.sendSystemMessage(Component.translatable(
                "power.neoorigins.tame_mob.not_enough_hunger").withStyle(ChatFormatting.RED), true);
            return false;
        }

        // Raycast for an entity
        Entity target = getTargetEntity(player, config.range());
        if (target == null) {
            player.sendSystemMessage(Component.translatable(
                "power.neoorigins.tame_mob.no_target").withStyle(ChatFormatting.YELLOW), true);
            return false;
        }

        // Must be a hostile Mob, not a boss
        if (!(target instanceof Mob mob) || !(target instanceof Enemy)) {
            player.sendSystemMessage(Component.translatable(
                "power.neoorigins.tame_mob.not_hostile").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!mob.canUsePortal(false)) {
            // Boss mobs (Ender Dragon, Wither) cannot use portals
            player.sendSystemMessage(Component.translatable(
                "power.neoorigins.tame_mob.boss").withStyle(ChatFormatting.RED), true);
            return false;
        }

        // Rewrite AI
        rewriteAI(mob, player);

        // Persistence so it doesn't despawn
        mob.setPersistenceRequired();

        // Track via MinionTracker
        MinionTracker.track(player, mob, TAMED_MOB_KEY,
            player.tickCount, config.despawnTicks(), config.deathDamage());

        // Consume hunger
        player.getFoodData().setFoodLevel(player.getFoodData().getFoodLevel() - config.hungerCost());

        // Effects
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
            SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 1.0f, 1.2f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
            mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(),
            15, 0.4, 0.4, 0.4, 0.02);

        player.sendSystemMessage(Component.translatable(
            "power.neoorigins.tame_mob.success", mob.getName()).withStyle(ChatFormatting.GREEN), true);

        return true;
    }

    /**
     * Strips hostile-to-player goals and adds follow-owner + defend-owner behavior.
     */
    @SuppressWarnings("unchecked")
    private static void rewriteAI(Mob mob, ServerPlayer owner) {
        // Clear all targeting goals (removes NearestAttackableTargetGoal<Player>, etc.)
        mob.targetSelector.getAvailableGoals().clear();

        // Re-add HurtByTargetGoal so it fights back when hit (requires PathfinderMob).
        // Owner-aware subclass: accidental owner hits (collision, AoE, thorns
        // reflection) don't flip the mob hostile against the owner.
        if (mob instanceof PathfinderMob pathfinder) {
            mob.targetSelector.addGoal(1, new OwnerAwareHurtByTargetGoal(pathfinder, owner));
        }

        // Add a goal to defend the owner — target anything that recently hurt them
        mob.targetSelector.addGoal(2, new DefendOwnerGoal(mob, owner));

        // Remove any existing AvoidEntityGoal targeting players, then add follow-owner
        mob.goalSelector.getAvailableGoals().removeIf(
            g -> g.getGoal() instanceof AvoidEntityGoal);

        // Follow the owner at medium priority. Leash is intentionally loose
        // (24-block teleport, 8-block follow-start) so the pet has room to
        // engage enemies without snapping back to the owner every few steps.
        mob.goalSelector.addGoal(2, new FollowOwnerGoal(mob, owner, 24.0, 8.0, 1.0));
    }

    private static Entity getTargetEntity(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);

        double closestDist = range * range;
        Entity closest = null;

        for (Entity entity : player.level().getEntities(player, searchBox, e -> e instanceof LivingEntity && e.isAlive())) {
            AABB entityBB = entity.getBoundingBox().inflate(entity.getPickRadius());
            Optional<Vec3> hitVec = entityBB.clip(eye, end);
            if (hitVec.isPresent()) {
                double dist = eye.distanceToSqr(hitVec.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = entity;
                }
            }
        }
        return closest;
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        MinionTracker.clearAll(player.getUUID());
    }

    /** Returns the mob type key used for MinionTracker lookups. */
    public static String tamedMobKey() {
        return TAMED_MOB_KEY;
    }

    /**
     * Simple follow-owner goal for tamed hostile mobs.
     * The mob walks toward the owner when farther than startDist and teleports if too far.
     */
    private static class FollowOwnerGoal extends Goal {
        private final Mob mob;
        private final ServerPlayer owner;
        private final double teleportDist;
        private final double startDist;
        private final double speed;

        FollowOwnerGoal(Mob mob, ServerPlayer owner, double teleportDist, double startDist, double speed) {
            this.mob = mob;
            this.owner = owner;
            this.teleportDist = teleportDist;
            this.startDist = startDist;
            this.speed = speed;
        }

        @Override
        public boolean canUse() {
            return owner.isAlive() && mob.distanceToSqr(owner) > startDist * startDist;
        }

        @Override
        public boolean canContinueToUse() {
            return owner.isAlive() && mob.distanceToSqr(owner) > (startDist - 1) * (startDist - 1);
        }

        @Override
        public void tick() {
            mob.getLookControl().setLookAt(owner, 10.0f, (float) mob.getMaxHeadXRot());

            if (mob.distanceToSqr(owner) > teleportDist * teleportDist) {
                // Defuse primed creepers before the leash-teleport — otherwise
                // a tamed creeper that started its swell at a far-away target
                // detonates on top of the owner the moment it arrives.
                if (mob instanceof net.minecraft.world.entity.monster.Creeper creeper) {
                    creeper.setSwellDir(-1);
                }
                mob.teleportTo(owner.getX() + (mob.getRandom().nextDouble() - 0.5) * 2,
                    owner.getY(), owner.getZ() + (mob.getRandom().nextDouble() - 0.5) * 2);
            } else {
                mob.getNavigation().moveTo(owner, speed);
            }
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }
    }

    /**
     * HurtByTargetGoal variant that forgives the owner. When the owner's hit
     * is what flipped {@code lastHurtByMob}, clear it and decline to target —
     * otherwise accidental collision/AoE/thorns damage turns the pet against
     * its summoner.
     */
    public static class OwnerAwareHurtByTargetGoal extends HurtByTargetGoal {
        private final ServerPlayer owner;

        public OwnerAwareHurtByTargetGoal(PathfinderMob mob, ServerPlayer owner) {
            super(mob);
            this.owner = owner;
        }

        @Override
        public boolean canUse() {
            LivingEntity lastHurt = this.mob.getLastHurtByMob();
            if (lastHurt != null && lastHurt.getUUID().equals(owner.getUUID())) {
                this.mob.setLastHurtByMob(null);
                return false;
            }
            return super.canUse();
        }
    }

    /**
     * Custom targeting goal: the tamed mob targets whatever recently hurt its owner.
     * Avoids NearestAttackableTargetGoal constructor compatibility issues across MC versions.
     */
    public static class DefendOwnerGoal extends Goal {
        private final Mob mob;
        private final ServerPlayer owner;

        public DefendOwnerGoal(Mob mob, ServerPlayer owner) {
            this.mob = mob;
            this.owner = owner;
        }

        @Override
        public boolean canUse() {
            LivingEntity attacker = owner.getLastHurtByMob();
            return attacker != null && attacker.isAlive() && attacker != owner
                && owner.tickCount - owner.getLastHurtByMobTimestamp() < 100;
        }

        @Override
        public void start() {
            LivingEntity attacker = owner.getLastHurtByMob();
            if (attacker != null && attacker.isAlive()) {
                mob.setTarget(attacker);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return mob.getTarget() != null && mob.getTarget().isAlive();
        }

        @Override
        public void stop() {
            mob.setTarget(null);
        }
    }
}
