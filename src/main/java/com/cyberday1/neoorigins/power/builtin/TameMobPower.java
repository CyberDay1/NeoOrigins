package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.service.EntityExclusions;
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
        // Stored under a NON-interface name (hungerCostLegacy, not hungerCost)
        // on purpose: tame_mob charges hunger/resource PER MOB internally, so it
        // must keep AbstractActivePower.Config#hungerCost() at its default of 0
        // so the base class never pre-checks or debits hunger on activation.
        int hungerCostLegacy,
        int despawnTicks,
        float deathDamage,
        boolean hostileOnly,
        List<String> entityBlacklist,
        // Area-mode whitelist (entity ids / #tag refs); empty = allow all.
        List<String> entityWhitelist,
        // "raycast" (single look-target) or "area" (AoE in range). Case-folded
        // in execute(); anything unrecognized falls back to raycast.
        String targeting,
        // Per-mob resource cost. Stored under NON-interface names
        // (tameResourceId / tameResourceAmount, not resourceCost() /
        // resourceCostAmount()) so the base class's auto-charge stays disabled
        // and tame_mob handles all cost itself, charging once per mob tamed.
        String tameResourceId,
        int tameResourceAmount,
        String type,
        String cooldownIcon,
        boolean cooldownCountdown,
        boolean alwaysShowIcon
    ) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.DOUBLE.optionalFieldOf("range", 16.0).forGetter(Config::range),
            Codec.INT.optionalFieldOf("max_tamed", 4).forGetter(Config::maxTamed),
            Codec.INT.optionalFieldOf("cooldown_ticks", 200).forGetter(Config::cooldownTicks),
            Codec.INT.optionalFieldOf("hunger_cost", 3).forGetter(Config::hungerCostLegacy),
            Codec.INT.optionalFieldOf("despawn_ticks", 36000).forGetter(Config::despawnTicks),
            Codec.FLOAT.optionalFieldOf("death_damage", 0.5f).forGetter(Config::deathDamage),
            // Default true preserves the Monster Tamer feel (hostile mobs only).
            // Packs that want to tame any non-player Mob (animals, golems,
            // villagers, etc.) can set "hostile_only": false in their power JSON.
            Codec.BOOL.optionalFieldOf("hostile_only", true).forGetter(Config::hostileOnly),
            // Per-power blocklist of entity ids ("minecraft:warden") and tag
            // refs ("#mymod:untameable"). Checked on top of the built-in
            // boss-tier exclusion below.
            Codec.STRING.listOf().optionalFieldOf("entity_blacklist", List.of())
                .forGetter(Config::entityBlacklist),
            // Area-mode whitelist: same id/#tag syntax as entity_blacklist.
            // Empty (default) = any mob (subject to hostile_only + exclusions).
            Codec.STRING.listOf().optionalFieldOf("entity_whitelist", List.of())
                .forGetter(Config::entityWhitelist),
            Codec.STRING.optionalFieldOf("targeting", "raycast").forGetter(Config::targeting),
            Codec.STRING.optionalFieldOf("resource_cost", "").forGetter(Config::tameResourceId),
            Codec.INT.optionalFieldOf("resource_cost_amount", 0).forGetter(Config::tameResourceAmount),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type),
            Codec.STRING.optionalFieldOf("cooldown_icon", "").forGetter(Config::cooldownIcon),
            Codec.BOOL.optionalFieldOf("cooldown_countdown", true).forGetter(Config::cooldownCountdown),
            Codec.BOOL.optionalFieldOf("always_show_icon", false).forGetter(Config::alwaysShowIcon)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        // Check cap. slotsLeft caps how many mobs an area cast can grab.
        int alive = MinionTracker.countAlive(player.getUUID(), TAMED_MOB_KEY);
        int slotsLeft = config.maxTamed() - alive;
        if (slotsLeft <= 0) {
            player.sendSystemMessage(Component.translatable(
                "power.neoorigins.tame_mob.max_reached").withStyle(ChatFormatting.RED), true);
            return false;
        }

        // Cost mode resolved once for the whole activation. tame_mob does ALL
        // cost handling itself (per mob), so the base class charges nothing —
        // see the Config field-naming note above.
        boolean resourceConfigured = !config.tameResourceId().isEmpty() && config.tameResourceAmount() > 0;
        boolean barsDisabled = ContentTogglesConfig.isResourceBarsDisabled();

        boolean area = "area".equalsIgnoreCase(config.targeting());

        // Build the candidate list (nearest-first). RAYCAST = the single
        // look-target (0 or 1 mob), still gated by today's specific messages.
        // AREA = the shared AoE selector, which already applies hostile_only,
        // entity_blacklist, boss-tier, and the global config exclusions.
        List<Mob> candidates;
        if (area) {
            candidates = com.cyberday1.neoorigins.service.AreaTargetSelector.mobsInRadius(
                player, config.range(), config.entityWhitelist(), config.entityBlacklist(),
                config.hostileOnly(), slotsLeft);
        } else {
            Mob single = validateRaycastTarget(player, config);
            candidates = single == null ? List.of() : List.of(single);
        }

        int tamedCount = 0;
        Mob lastTamed = null;
        for (Mob mob : candidates) {
            if (tamedCount >= slotsLeft) break;
            // Per-mob portal-lock guard. In raycast mode the full validation
            // (incl. boss-tier) already ran; in area mode boss-tier is excluded
            // by the selector, but the legacy portal-lock flag still applies.
            if (!mob.canUsePortal(false)) continue;
            // Greedy: ran out of resource/hunger → tame what we could, stop.
            if (!canAffordOne(player, config, resourceConfigured, barsDisabled)) break;

            applyTame(player, mob, config.despawnTicks(), config.deathDamage());

            chargeOne(player, config, resourceConfigured, barsDisabled);
            tamedCount++;
            lastTamed = mob;
        }

        if (tamedCount == 0) {
            if (candidates.isEmpty()) {
                // No valid target at all. In raycast mode the specific failure
                // message (not_hostile / boss / blacklisted) was already sent by
                // validateRaycastTarget; only the empty-raycast case falls here.
                if (!area) {
                    player.sendSystemMessage(Component.translatable(
                        "power.neoorigins.tame_mob.no_target").withStyle(ChatFormatting.YELLOW), true);
                }
            } else {
                // Had candidates but couldn't afford even one.
                player.sendSystemMessage(Component.translatable(
                    "power.neoorigins.tame_mob.no_resource").withStyle(ChatFormatting.RED), true);
            }
            return false; // no cooldown consumed
        }

        if (tamedCount == 1) {
            player.sendSystemMessage(Component.translatable(
                "power.neoorigins.tame_mob.success", lastTamed.getName()).withStyle(ChatFormatting.GREEN), true);
        } else {
            player.sendSystemMessage(Component.translatable(
                "power.neoorigins.tame_mob.success_area", tamedCount).withStyle(ChatFormatting.GREEN), true);
        }
        return true;
    }

    /**
     * Raycast-mode validation: returns the single look-target Mob if it passes
     * every gate (non-player Mob; hostile_only→Enemy; portal-lock/boss-tier;
     * config + per-power blacklist), else sends the matching actionbar message
     * and returns {@code null}. Returns {@code null} silently when the raycast
     * found nothing (the caller sends the no_target message).
     */
    private static Mob validateRaycastTarget(ServerPlayer player, Config config) {
        Entity target = getTargetEntity(player, config.range());
        if (target == null) {
            NeoOrigins.LOGGER.debug("[tame_mob] {}: raycast within {} blocks found no LivingEntity",
                player.getName().getString(), config.range());
            return null; // caller emits no_target
        }
        if (!(target instanceof Mob mob)) {
            NeoOrigins.LOGGER.debug("[tame_mob] {}: target {} is not a Mob ({})",
                player.getName().getString(), target.getName().getString(),
                target.getClass().getSimpleName());
            player.sendSystemMessage(Component.translatable(
                "power.neoorigins.tame_mob.not_hostile").withStyle(ChatFormatting.RED), true);
            return null;
        }
        if (config.hostileOnly() && !(target instanceof Enemy)) {
            NeoOrigins.LOGGER.debug("[tame_mob] {}: target {} ({}) is not hostile (Enemy); set hostile_only=false to allow",
                player.getName().getString(), target.getName().getString(),
                target.getClass().getSimpleName());
            player.sendSystemMessage(Component.translatable(
                "power.neoorigins.tame_mob.not_hostile").withStyle(ChatFormatting.RED), true);
            return null;
        }
        if (!mob.canUsePortal(false) || EntityExclusions.isBossTier(mob)) {
            // canUsePortal(false) is the legacy "can't tame bosses" gate (Ender
            // Dragon, Wither). The shared boss-tier set adds boss-grade mobs
            // that pass that check — the Warden was tameable because it isn't
            // portal-locked (Discord report), and the dragon stays listed
            // explicitly so the exclusion no longer depends on a portal-flag
            // side effect.
            NeoOrigins.LOGGER.debug("[tame_mob] {}: target {} ({}) is boss-tier or portal-locked",
                player.getName().getString(), mob.getName().getString(),
                mob.getClass().getSimpleName());
            player.sendSystemMessage(Component.translatable(
                "power.neoorigins.tame_mob.boss").withStyle(ChatFormatting.RED), true);
            return null;
        }
        // Blocklists: the server-operator global list (config) and the
        // pack-author per-power entity_blacklist. Both take entity ids and
        // #tag refs, same syntax as entity_types filters elsewhere
        // (scare_entities, action_on_hit). Shared logic: EntityExclusions.
        if (EntityExclusions.isConfigBlacklisted(mob)
                || EntityExclusions.matchesAny(mob, config.entityBlacklist())) {
            NeoOrigins.LOGGER.debug("[tame_mob] {}: target {} ({}) blocked by global or per-power entity blacklist",
                player.getName().getString(), mob.getName().getString(),
                mob.getClass().getSimpleName());
            player.sendSystemMessage(Component.translatable(
                "power.neoorigins.tame_mob.blacklisted").withStyle(ChatFormatting.RED), true);
            return null;
        }
        return mob;
    }

    /**
     * True if the player can pay for one more tame. Uses the resource bar when a
     * resource is configured and bars are enabled; otherwise charges hunger —
     * either the configured resource amount (bars disabled fallback) or the
     * legacy hunger_cost when no resource is configured.
     */
    private static boolean canAffordOne(ServerPlayer player, Config config,
            boolean resourceConfigured, boolean barsDisabled) {
        if (resourceConfigured && !barsDisabled) {
            return com.cyberday1.neoorigins.power.builtin.ResourcePower.getValue(
                player, config.tameResourceId()) >= config.tameResourceAmount();
        }
        int h = resourceConfigured ? config.tameResourceAmount() : config.hungerCostLegacy();
        return player.getFoodData().getFoodLevel() >= h;
    }

    /** Debit the cost of one tame, mirroring {@link #canAffordOne}'s mode. */
    private static void chargeOne(ServerPlayer player, Config config,
            boolean resourceConfigured, boolean barsDisabled) {
        if (resourceConfigured && !barsDisabled) {
            com.cyberday1.neoorigins.power.builtin.ResourcePower.deduct(
                player, config.tameResourceId(), config.tameResourceAmount());
            return;
        }
        int h = resourceConfigured ? config.tameResourceAmount() : config.hungerCostLegacy();
        player.getFoodData().setFoodLevel(player.getFoodData().getFoodLevel() - h);
    }

    /**
     * Strips hostile-to-player goals and adds follow-owner + defend-owner behavior.
     */
    @SuppressWarnings("unchecked")
    private static void rewriteAI(Mob mob, ServerPlayer owner) {
        // Clear all targeting goals (removes NearestAttackableTargetGoal<Player>, etc.)
        mob.targetSelector.getAvailableGoals().clear();

        // Drop any in-progress attack on the new owner this tick. Clearing the
        // goals stops re-acquisition, but the mob's *current* target persists
        // until vanilla times it out — so a mob caught mid-swing keeps attacking
        // the owner "until it loses sight" (Discord report). Also forgive a
        // pre-tame hit so HurtByTargetGoal doesn't instantly re-aggro the owner.
        if (mob.getTarget() == owner) mob.setTarget(null);
        if (mob.getLastHurtByMob() == owner) mob.setLastHurtByMob(null);

        // Re-add HurtByTargetGoal so it fights back when hit (requires PathfinderMob).
        // Owner-aware subclass: accidental owner hits (collision, AoE, thorns
        // reflection) don't flip the mob hostile against the owner.
        // Priority 0 — must beat the defend/aggro goals so a direct hit on the
        // pet always takes precedence over "owner is busy elsewhere."
        if (mob instanceof PathfinderMob pathfinder) {
            mob.targetSelector.addGoal(0, new OwnerAwareHurtByTargetGoal(pathfinder, owner));
        }

        // DEFEND: target whoever last attacked the owner. Reads
        // owner.getLastHurtByMob() directly (no spatial gate) so attackers
        // outside the pet's follow-distance still trigger defense.
        // Priority 1 (matches vanilla OwnerHurtByTargetGoal).
        mob.targetSelector.addGoal(1, new DefendOwnerGoal(mob, owner));

        // AGGRO: target whatever the owner is currently attacking. Modeled on
        // vanilla OwnerHurtTargetGoal — reads owner.getLastHurtMob(). Priority
        // 2 (matches vanilla; below defend so the pet prefers to peel attackers
        // off the owner over chasing the owner's chosen target).
        //
        // Previously this slot held a NearestAttackableTargetGoal whose
        // predicate was actually checking getLastHurtByMob (defend logic) — so
        // aggro was missing entirely and defend was duplicated with a buggy
        // spatial gate. See v2.1.6 backlog #6.
        mob.targetSelector.addGoal(2, new AggroWithOwnerGoal(mob, owner));

        // Remove player-avoidance and (for bees) flower-chasing goals, then add
        // follow-owner. Shared with SummonMinionPower so tamed and summoned bees
        // behave the same.
        SummonMinionPower.stripDistractionGoals(mob);

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
     * Convert {@code mob} into a tamed pet owned by {@code owner}: rewrite its AI
     * to follow/defend the owner, mark it persistent, register it with the
     * {@link MinionTracker} (despawn + death-damage tracking), and play the tame
     * sound/particle FX. Shared entrypoint so the {@code neoorigins:tame_target}
     * entity action tames with the exact same behaviour as this active power. The
     * caller owns the max-tamed cap check and any cost/validation.
     */
    public static void applyTame(ServerPlayer owner, Mob mob, int despawnTicks, float deathDamage) {
        rewriteAI(mob, owner);
        mob.setPersistenceRequired();
        MinionTracker.track(owner, mob, TAMED_MOB_KEY, owner.tickCount, despawnTicks, deathDamage);

        ServerLevel level = (ServerLevel) owner.level();
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
            SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 1.0f, 1.2f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
            mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(),
            15, 0.4, 0.4, 0.4, 0.02);
    }

    /**
     * Pure tameability gate (no player messaging): {@code target} must be a
     * non-boss, non-portal-locked {@link Mob}, optionally {@link Enemy}-only, and
     * absent from both the global config blacklist and the supplied per-power
     * blacklist. Mirrors the gates {@link #validateRaycastTarget} enforces, minus
     * the per-failure actionbar messages — used by the {@code tame_target} action.
     */
    public static boolean isTameable(Entity target, boolean hostileOnly, java.util.List<String> entityBlacklist) {
        if (!(target instanceof Mob mob)) return false;
        if (hostileOnly && !(target instanceof Enemy)) return false;
        if (!mob.canUsePortal(false) || EntityExclusions.isBossTier(mob)) return false;
        return !EntityExclusions.isConfigBlacklisted(mob)
            && !EntityExclusions.matchesAny(mob, entityBlacklist);
    }

    /** Crosshair look-target mob within {@code range}, or {@code null}. Public for the tame_target action. */
    public static Mob lookTargetMob(ServerPlayer player, double range) {
        return getTargetEntity(player, range) instanceof Mob mob ? mob : null;
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
                assignCombatTarget(mob, attacker);
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

    /**
     * Aggro goal: targets whatever the owner is currently attacking. Vanilla
     * parallel is {@code OwnerHurtTargetGoal}. Reads {@code owner.getLastHurtMob()}
     * and gates on {@code getLastHurtMobTimestamp()} so the pet doesn't keep
     * re-targeting the same dead enemy. Mirrors the structure of DefendOwnerGoal
     * (master's existing direct-Goal subclass design).
     */
    public static class AggroWithOwnerGoal extends Goal {
        private final Mob mob;
        private final ServerPlayer owner;
        private int lastSeenTimestamp;

        public AggroWithOwnerGoal(Mob mob, ServerPlayer owner) {
            this.mob = mob;
            this.owner = owner;
        }

        @Override
        public boolean canUse() {
            if (!owner.isAlive()) return false;
            int t = owner.getLastHurtMobTimestamp();
            if (t == this.lastSeenTimestamp) return false;
            LivingEntity target = owner.getLastHurtMob();
            if (target == null || !target.isAlive()) return false;
            if (target == owner) return false;
            if (target.getUUID().equals(owner.getUUID())) return false;
            return true;
        }

        @Override
        public void start() {
            LivingEntity target = owner.getLastHurtMob();
            if (target != null && target.isAlive()) {
                assignCombatTarget(mob, target);
                this.lastSeenTimestamp = owner.getLastHurtMobTimestamp();
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

    /**
     * Assign a combat target in a way that drives both goal-based and
     * brain-based mobs. Goal mobs act on {@link Mob#getTarget()}; brain-driven
     * neutral mobs (piglins, hoglins) ignore that and re-derive their target
     * from the {@code ATTACK_TARGET} brain memory each tick, so we write that
     * memory too. The brain write is a no-op on mobs that don't register the
     * slot, and the per-tick MinionTracker pacifier only clears targets aimed at
     * the owner — so an enemy target set here sticks.
     */
    static void assignCombatTarget(Mob mob, LivingEntity target) {
        mob.setTarget(target);
        var brain = mob.getBrain();
        // Piglins/hoglins drive combat through their anger pipeline. A bare
        // ATTACK_TARGET with no matching ANGRY_AT is treated as invalid and
        // erased the next brain tick, which fought the goal that set it —
        // producing the rapid weapon draw/sheathe and repeated anger sound.
        // Seed the anger memory (with the same expiries vanilla PiglinAi uses)
        // so the brain keeps the target alive on its own.
        if (mob instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin
                || mob instanceof net.minecraft.world.entity.monster.hoglin.Hoglin) {
            brain.setMemoryWithExpiry(
                net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT,
                target.getUUID(), 600L);
            brain.setMemoryWithExpiry(
                net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET,
                target, 200L);
        } else {
            brain.setMemory(
                net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, target);
        }
    }
}
