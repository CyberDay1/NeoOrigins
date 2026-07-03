package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.attachment.EntityAttachments;
import com.cyberday1.neoorigins.attachment.EntityAttachments.MinionOwner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks summoned minions per player. Handles despawn timers and
 * notifies the summoner when a minion dies in combat.
 *
 * <p>Minions are tracked by UUID rather than by Java reference so that
 * entries survive dimension changes — when a minion walks through a portal,
 * vanilla creates a new {@link Entity} instance in the target dimension but
 * preserves the UUID. Storing the old reference made {@code isAlive()}
 * return false and the tracker silently forget the minion. Resolving by
 * UUID via {@link MinecraftServer#getAllLevels()} picks up the new instance
 * in whichever dimension it's currently loaded in.
 */
public final class MinionTracker {

    private MinionTracker() {}

    public record TrackedMinion(UUID minionUuid, String mobType, int spawnTick, int despawnTick, float deathDamage) {
        /**
         * Resolves this minion's current entity. Checks the last known dimension
         * first (stamped at track-time or on previous successful resolve); falls
         * back to scanning all loaded dimensions only if the hint misses, which
         * happens on dimension-change or server restart. The hint is updated
         * when the scan locates the minion in a different dimension so
         * subsequent calls stay fast.
         */
        public LivingEntity entity() {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return null;

            var hintKey = DIM_HINTS.get(minionUuid);
            if (hintKey != null) {
                ServerLevel hintLevel = server.getLevel(hintKey);
                if (hintLevel != null) {
                    Entity e = hintLevel.getEntity(minionUuid);
                    if (e instanceof LivingEntity le && !le.isRemoved()) return le;
                }
            }

            for (ServerLevel level : server.getAllLevels()) {
                if (level.dimension().equals(hintKey)) continue;
                Entity e = level.getEntity(minionUuid);
                if (e instanceof LivingEntity le && !le.isRemoved()) {
                    DIM_HINTS.put(minionUuid, level.dimension());
                    return le;
                }
            }
            return null;
        }
    }

    /** Player UUID → list of tracked minions. */
    private static final Map<UUID, List<TrackedMinion>> MINIONS = new ConcurrentHashMap<>();

    /** Minion UUID → last-known dimension. Used by {@link TrackedMinion#entity()}
     *  to skip the N-dimension scan. Stamped on {@link #track(ServerPlayer, LivingEntity, String, int, int, float)}
     *  and refreshed whenever a scan finds the minion in a different dimension. */
    private static final Map<UUID, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> DIM_HINTS =
        new ConcurrentHashMap<>();

    /** Register a newly summoned minion. Also stamps the mob with a persistent
     * {@code minion_owner} attachment so ownership survives dimension changes
     * and server restarts (the in-memory {@link #MINIONS} map is session-scoped). */
    public static void track(ServerPlayer summoner, LivingEntity minion, String mobType,
                             int currentTick, int despawnTicks, float deathDamage) {
        MINIONS.computeIfAbsent(summoner.getUUID(), k -> new java.util.concurrent.CopyOnWriteArrayList<>())
            .add(new TrackedMinion(minion.getUUID(), mobType, currentTick, currentTick + despawnTicks, deathDamage));
        minion.setData(EntityAttachments.minionOwner(), MinionOwner.of(summoner.getUUID()));
        if (minion.level() instanceof ServerLevel sl) {
            DIM_HINTS.put(minion.getUUID(), sl.dimension());
        }
    }

    /** Count living minions of a given mob type for a player. */
    public static int countAlive(UUID playerUuid, String mobType) {
        List<TrackedMinion> list = MINIONS.get(playerUuid);
        if (list == null) return 0;
        int count = 0;
        for (TrackedMinion m : list) {
            if (!m.mobType().equals(mobType)) continue;
            LivingEntity entity = m.entity();
            if (entity != null && entity.isAlive()) count++;
        }
        return count;
    }

    /**
     * Called every server tick per player. Despawns expired minions and
     * removes dead entries.
     */
    public static void tick(ServerPlayer player) {
        List<TrackedMinion> list = MINIONS.get(player.getUUID());
        if (list == null || list.isEmpty()) return;

        int currentTick = player.tickCount;
        // CopyOnWriteArrayList's iterator doesn't support remove(); collect
        // entries to remove in a first pass, then drop them via removeAll
        // (which is supported via atomic snapshot replacement).
        List<TrackedMinion> toRemove = new ArrayList<>();
        for (TrackedMinion m : list) {
            LivingEntity entity = m.entity();

            // Entity not resolvable (in an unloaded chunk, or already gone).
            // Keep the entry until despawn time so we don't evict minions that
            // are just temporarily unloaded.
            if (entity == null) {
                if (currentTick >= m.despawnTick()) {
                    toRemove.add(m);
                    DIM_HINTS.remove(m.minionUuid());
                }
                continue;
            }

            if (!entity.isAlive()) {
                toRemove.add(m);
                DIM_HINTS.remove(m.minionUuid());
                continue;
            }
            if (currentTick >= m.despawnTick()) {
                entity.discard();
                toRemove.add(m);
                DIM_HINTS.remove(m.minionUuid());
                continue;
            }

            // Keep brain-driven neutral mobs (piglins/hoglins) from turning on
            // their summoner. Their anger lives in the Brain/memory system, not
            // in goal selectors, so it re-arms every few ticks (e.g. a piglin
            // re-checks "is the player wearing gold?") and bypasses the
            // goal-based LivingChangeTargetEvent interceptor entirely.
            pacifyTowardOwner(entity, player);
            driveBrainMinion(entity, player);
        }
        if (!toRemove.isEmpty()) {
            list.removeAll(toRemove);
        }
    }

    /**
     * Per-tick anger suppression for brain-driven neutral mobs. Clears any
     * attack-target or anger memory pointed at the owner (and the goal-selector
     * target for non-brain mobs), and keeps piglins immune to zombification so
     * they don't re-arm against an un-gold-armoured summoner.
     */
    private static void pacifyTowardOwner(LivingEntity minion, ServerPlayer owner) {
        if (minion instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin piglin) {
            piglin.setImmuneToZombification(true);
        }

        var brain = minion.getBrain();

        if (brain.checkMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET,
                net.minecraft.world.entity.ai.memory.MemoryStatus.VALUE_PRESENT)) {
            var target = brain.getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET)
                .orElse(null);
            if (target == owner) {
                brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
            }
        }

        if (brain.checkMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT,
                net.minecraft.world.entity.ai.memory.MemoryStatus.VALUE_PRESENT)) {
            var angryAt = brain.getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT)
                .orElse(null);
            if (owner.getUUID().equals(angryAt)) {
                brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT);
                if (minion instanceof net.minecraft.world.entity.NeutralMob neutral) {
                    neutral.stopBeingAngry();
                }
            }
        }

        // Goal-based mobs are normally handled by the LivingChangeTargetEvent
        // interceptor, but clear here too as a belt-and-braces against any
        // target that slipped through (e.g. set directly without the event).
        if (minion instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null
                && (mob.getTarget() == owner
                    || isTrackedMinionOf(mob.getTarget(), owner.getUUID()))) {
            mob.setTarget(null);
        }
    }

    /**
     * Brain-driven mobs (piglins, hoglins) ignore the goal-based follow/target
     * goals installed on summoned minions: their movement is controlled solely
     * by the {@code WALK_TARGET} brain memory, which a {@code MoveToTargetSink}
     * behaviour overrides any navigation a goal sets. That's why a summoned
     * piglin neither leashes to its owner nor chases a target until the owner
     * walks right up to it. Drive movement here through the brain instead:
     * chase a live combat target, otherwise leash to the owner using the same
     * 8-block start / 24-block teleport distances as the goal-based
     * {@code FollowOwnerGoal} so the two mob families behave alike.
     */
    private static void driveBrainMinion(LivingEntity minion, ServerPlayer owner) {
        if (!(minion instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin)
                && !(minion instanceof net.minecraft.world.entity.monster.hoglin.Hoglin)) {
            return;
        }
        if (!(minion instanceof net.minecraft.world.entity.Mob mob)) return;
        var brain = mob.getBrain();

        LivingEntity target = brain.getMemory(
                net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET).orElse(null);
        // Never chase a fellow minion of the same owner (e.g. one the owner
        // clipped by accident) — drop the target so it falls back to leashing.
        if (target != null && isTrackedMinionOf(target, owner.getUUID())) {
            brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
            brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT);
            target = null;
        }
        if (target != null && target.isAlive() && target != owner) {
            // Keep anger fresh so the FIGHT activity doesn't drop the target,
            // and force a walk target so the piglin chases instead of waiting
            // for the enemy to wander into its sensor range.
            brain.setMemoryWithExpiry(
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT,
                    target.getUUID(), 600L);
            brain.setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
                    new net.minecraft.world.entity.ai.memory.WalkTarget(target, 1.0f, 2));
            return;
        }

        // No combat — leash to the owner.
        double dsq = mob.distanceToSqr(owner);
        if (dsq > 24.0 * 24.0) {
            mob.snapTo(owner.getX() + (mob.getRandom().nextDouble() - 0.5) * 2,
                    owner.getY(), owner.getZ() + (mob.getRandom().nextDouble() - 0.5) * 2,
                    mob.getYRot(), mob.getXRot());
            brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
        } else if (dsq > 8.0 * 8.0) {
            mob.getLookControl().setLookAt(owner, 10.0f, (float) mob.getMaxHeadXRot());
            brain.setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
                    new net.minecraft.world.entity.ai.memory.WalkTarget(owner, 1.0f, 6));
        }
    }

    /**
     * Called when any LivingEntity dies. If it's a tracked minion that died
     * from combat (not discarded/despawned), damage the summoner.
     */
    public static void onEntityDeath(LivingEntity entity) {
        UUID entityUuid = entity.getUUID();
        for (var entry : MINIONS.entrySet()) {
            List<TrackedMinion> list = entry.getValue();
            TrackedMinion match = null;
            for (TrackedMinion m : list) {
                if (m.minionUuid().equals(entityUuid)) {
                    match = m;
                    break;
                }
            }
            if (match == null) continue;
            // CopyOnWriteArrayList's iterator doesn't support remove(); use remove(Object).
            list.remove(match);
            DIM_HINTS.remove(entityUuid);
            // Damage the summoner — the entity died in combat, not from discard
            ServerPlayer summoner = entity.level().getServer() != null
                ? entity.level().getServer().getPlayerList().getPlayer(entry.getKey())
                : null;
            if (summoner != null && match.deathDamage() > 0) {
                summoner.hurt(summoner.damageSources().magic(), match.deathDamage());
                NeoOrigins.LOGGER.debug("Necromancer {} took {} backlash damage from minion death",
                    summoner.getName().getString(), match.deathDamage());
            }
            return;
        }
    }

    /**
     * True if {@code entity} is currently tracked as a minion summoned by the
     * player identified by {@code summonerUuid}. Used by the targeting
     * interceptor to stop summoned mobs from attacking their own summoner.
     *
     * <p>Checks the persistent {@code minion_owner} attachment first (survives
     * dimension changes + server restarts), then falls back to the in-memory
     * map so entries from older saves without the attachment still resolve
     * during the current session.
     */
    public static boolean isTrackedMinionOf(Entity entity, UUID summonerUuid) {
        if (entity == null) return false;
        MinionOwner owner = entity.getData(EntityAttachments.minionOwner());
        if (owner.isOwnedBy(summonerUuid)) return true;
        List<TrackedMinion> list = MINIONS.get(summonerUuid);
        if (list == null) return false;
        UUID entityUuid = entity.getUUID();
        for (TrackedMinion m : list) {
            if (m.minionUuid().equals(entityUuid)) return true;
        }
        return false;
    }

    /**
     * Reverse lookup: return the ServerPlayer who summoned {@code entity}
     * if it is a currently-tracked minion. Checks the persistent
     * {@code minion_owner} attachment first (O(1) per-entity), then falls
     * back to scanning the in-memory map for session-only entries.
     *
     * @return the online summoner, or empty if the entity is unsummoned
     *         or the summoner is offline.
     */
    public static Optional<ServerPlayer> summonerOf(LivingEntity entity) {
        if (entity == null) return Optional.empty();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return Optional.empty();

        MinionOwner owner = entity.getData(EntityAttachments.minionOwner());
        if (owner.ownerUuid().isPresent()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(owner.ownerUuid().get());
            if (sp != null) return Optional.of(sp);
        }

        UUID entityUuid = entity.getUUID();
        for (var entry : MINIONS.entrySet()) {
            for (TrackedMinion m : entry.getValue()) {
                if (m.minionUuid().equals(entityUuid)) {
                    ServerPlayer sp = server.getPlayerList().getPlayer(entry.getKey());
                    return Optional.ofNullable(sp);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * True if {@code entity} is a summoned minion for any player. Used by the
     * loot-drop interceptor — summoned mobs should never leave gear/items behind
     * because the summoner effectively conjured them (and their equipment) for
     * free. Checks the persistent attachment first, then the in-memory map.
     */
    public static boolean isAnyTrackedMinion(Entity entity) {
        if (entity == null) return false;
        if (entity.getData(EntityAttachments.minionOwner()).isOwned()) return true;
        UUID entityUuid = entity.getUUID();
        for (List<TrackedMinion> list : MINIONS.values()) {
            for (TrackedMinion m : list) {
                if (m.minionUuid().equals(entityUuid)) return true;
            }
        }
        return false;
    }

    /** Get all living tracked minions of a given mob type for a player. */
    public static List<TrackedMinion> getAlive(UUID playerUuid, String mobType) {
        List<TrackedMinion> list = MINIONS.get(playerUuid);
        if (list == null) return List.of();
        List<TrackedMinion> alive = new ArrayList<>();
        for (TrackedMinion m : list) {
            if (!m.mobType().equals(mobType)) continue;
            LivingEntity entity = m.entity();
            if (entity != null && entity.isAlive()) alive.add(m);
        }
        return alive;
    }

    /** Clean up all minions for a player (e.g., on logout or origin change). */
    public static void clearAll(UUID playerUuid) {
        List<TrackedMinion> list = MINIONS.remove(playerUuid);
        if (list != null) {
            for (TrackedMinion m : list) {
                LivingEntity entity = m.entity();
                if (entity != null && entity.isAlive()) entity.discard();
                DIM_HINTS.remove(m.minionUuid());
            }
        }
    }

    /**
     * Clean up all minions for a player EXCEPT those of {@code keepMobType}.
     * Used on the summoner's death: conjured minions (necromancer summons etc.)
     * shouldn't outlive their owner, but tamed pets get vanilla-pet semantics —
     * they survive the tamer's death and are recalled to the tamer on respawn
     * (see {@code TameMobPower.recallTamedOnRespawn}). Kept entries stay
     * tracked, so despawn timers, death-damage and per-tick pacification keep
     * working across the owner's death.
     */
    public static void clearAllExceptType(UUID playerUuid, String keepMobType) {
        List<TrackedMinion> list = MINIONS.get(playerUuid);
        if (list == null) return;
        List<TrackedMinion> toRemove = new ArrayList<>();
        for (TrackedMinion m : list) {
            if (m.mobType().equals(keepMobType)) continue;
            LivingEntity entity = m.entity();
            if (entity != null && entity.isAlive()) entity.discard();
            DIM_HINTS.remove(m.minionUuid());
            toRemove.add(m);
        }
        if (!toRemove.isEmpty()) list.removeAll(toRemove);
        if (list.isEmpty()) MINIONS.remove(playerUuid);
    }
}
