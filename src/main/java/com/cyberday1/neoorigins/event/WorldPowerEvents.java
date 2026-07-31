package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.builtin.*;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.VanillaGameEvent;

import java.util.*;

@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public class WorldPowerEvents {

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof ServerPlayer sp)) return;

        // Summoned minions must never target their own summoner. Overrides the
        // retaliation window below — even if the player hits their own minion
        // by accident, the minion should not turn on them.
        if (com.cyberday1.neoorigins.service.MinionTracker.isTrackedMinionOf(event.getEntity(), sp.getUUID())) {
            event.setCanceled(true);
            return;
        }

        // ScareEntitiesPower — mobs that the player is scaring shouldn't be
        // able to target them in the first place. This runs BEFORE the
        // MobsIgnorePlayer check so scare-matched mobs are locked out even if
        // the MobsIgnore retaliation window has already expired. Retaliation
        // still works: if the mob is the last hurt-by source for the player
        // within vanilla's timer, we allow the target change so combat
        // feedback loops work.
        // Boss-tier mobs, the global tame_scare_entity_blacklist config and a
        // power's own entity_blacklist are exempt (EntityExclusions): they
        // target the player normally regardless of scare/ignore powers.
        if (ActiveOriginService.has(sp, com.cyberday1.neoorigins.power.builtin.ScareEntitiesPower.class,
                cfg -> cfg.entityTypes().stream().anyMatch(id ->
                        com.cyberday1.neoorigins.event.CombatPowerEvents.matchesEntityIdOrTag(event.getEntity(), id))
                    && !com.cyberday1.neoorigins.service.EntityExclusions.isExcluded(
                        event.getEntity(), cfg.entityBlacklist()))) {
            if (event.getEntity().getLastHurtByMob() == sp) return;
            event.setCanceled(true);
            return;
        }

        // MobsIgnorePlayer matches when (a) the mob type is in entity_types
        // (or entity_types is empty = match-all) and (b) either passive=true
        // OR the player hasn't recently hit this mob. We capture whether any
        // matching config is passive so the retaliation-window decision below
        // can honor it.
        boolean[] passiveMatch = {false};
        boolean matched = ActiveOriginService.has(sp, MobsIgnorePlayerPower.class, cfg -> {
            boolean typeMatch = cfg.entityTypes().isEmpty()
                || cfg.entityTypes().stream().anyMatch(id ->
                    com.cyberday1.neoorigins.event.CombatPowerEvents.matchesEntityIdOrTag(event.getEntity(), id));
            // Shared exclusions (boss-tier / global config / per-power
            // entity_blacklist) override the match — the mob targets normally.
            // Matters especially for the empty match-all entity_types case,
            // which would otherwise let a player stand in front of the Warden.
            if (typeMatch && com.cyberday1.neoorigins.service.EntityExclusions.isExcluded(
                    event.getEntity(), cfg.entityBlacklist())) {
                typeMatch = false;
            }
            if (typeMatch && cfg.passive()) passiveMatch[0] = true;
            return typeMatch;
        });
        if (matched) {
            // Retaliation window: if the player recently hit this mob, allow
            // targeting so the mob can fight back — UNLESS the matching
            // config has passive=true, which makes the ignore unconditional.
            // Vanilla clears getLastHurtByMob() on its own timer.
            if (!passiveMatch[0] && event.getEntity().getLastHurtByMob() == sp) return;
            event.setCanceled(true);
            return;
        }

        // EntityGroupPower ignored_by — a group def may list entity ids / #tags
        // whose mobs treat this player as if they were the same group (e.g. the
        // built-in illager group makes raiders ignore you). Simulated here since
        // a player can't join the real EntityType tag the mob's AI checks. No
        // retaliation window: group membership is a standing trait, not a
        // scare/ignore toggle, so it holds even if the player has hit the mob.
        if (ActiveOriginService.has(sp, EntityGroupPower.class,
                cfg -> cfg.groupDef().ignoredBy(event.getEntity()))) {
            event.setCanceled(true);
            return;
        }

        // SneakyPower — reduce detection range
        if (ActiveOriginService.has(sp, SneakyPower.class, c -> true)) {
            var mob = event.getEntity();
            double dist = mob.distanceTo(sp);
            var followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
            double range = followRange != null ? followRange.getValue() : 16.0;
            final double[] mult = {1.0};
            ActiveOriginService.forEachOfType(sp, SneakyPower.class, cfg ->
                mult[0] = Math.min(mult[0], cfg.detectionMultiplier()));
            if (dist > range * mult[0]) {
                event.setCanceled(true);
                return;
            }
        }

        // StealthPower — if player has been sneaking long enough, cancel targeting
        if (ActiveOriginService.has(sp, StealthPower.class, c -> true)) {
            if (sp.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        Mob mob = event.getEntity();
        if (!anyWardingPlayer(sl)) return;
        for (ServerPlayer sp : sl.players()) {
            ActiveOriginService.forEachOfTypeActive(sp, NoMobSpawnsNearbyPower.class, cfg -> {
                double radius = cfg.radius();
                if (sp.distanceToSqr(mob) <= radius * radius && matchesSpawnCategory(cfg, mob)) {
                    event.setSpawnCancelled(true);
                }
            });
        }
    }

    /** Immutable snapshot so an off-thread reader can never see a torn gate. */
    private record SpawnWardGate(ResourceKey<Level> dim, int tick, boolean warded) {}

    private static volatile SpawnWardGate spawnWardGate = null;

    /**
     * True if anyone in {@code sl} might be warding mob spawns. FinalizeSpawn fires
     * for every monster spawn attempt, and virtually nobody holds
     * {@code no_mob_spawns_nearby}, so the scan above is almost always waste.
     *
     * <p>Recomputed at most once per (dimension, server tick) from the same
     * authoritative power lists the scan reads. The probe ignores conditions and
     * toggles, so it over-approximates and a warded gate always falls through to
     * the exact scan; the only staleness is that a ward granted mid-tick starts
     * suppressing spawns on the next tick. Chunk-generation spawns can enter here
     * off-thread, hence the single-volatile snapshot.
     */
    private static boolean anyWardingPlayer(ServerLevel sl) {
        int tick = sl.getServer().getTickCount();
        ResourceKey<Level> dim = sl.dimension();
        SpawnWardGate cached = spawnWardGate;
        if (cached != null && cached.tick() == tick && cached.dim().equals(dim)) {
            return cached.warded();
        }
        boolean warded = false;
        for (ServerPlayer sp : sl.players()) {
            if (ActiveOriginService.has(sp, NoMobSpawnsNearbyPower.class, c -> true)) {
                warded = true;
                break;
            }
        }
        spawnWardGate = new SpawnWardGate(dim, tick, warded);
        return warded;
    }

    private static boolean matchesSpawnCategory(NoMobSpawnsNearbyPower.Config cfg, Mob mob) {
        if (cfg.coversAll()) return true;
        MobCategory cat = mob.getType().getCategory();
        for (String s : cfg.categories()) {
            if ("monster".equalsIgnoreCase(s)        && cat == MobCategory.MONSTER)        return true;
            if ("creature".equalsIgnoreCase(s)       && cat == MobCategory.CREATURE)       return true;
            if ("ambient".equalsIgnoreCase(s)        && cat == MobCategory.AMBIENT)        return true;
            if ("water_creature".equalsIgnoreCase(s) && cat == MobCategory.WATER_CREATURE) return true;
            if ("water_ambient".equalsIgnoreCase(s)  && cat == MobCategory.WATER_AMBIENT)  return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        float scaled = com.cyberday1.neoorigins.service.EventPowerIndex.dispatchModifier(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_NATURAL_REGEN, null, event.getAmount());
        if (scaled != event.getAmount()) {
            // Defence-in-depth clamp against non-finite results — see
            // CombatPowerEvents.onLivingDamage for the full story of
            // how an unclamped multiply can brick a save via NaN health.
            if (!Float.isFinite(scaled)) scaled = Float.MAX_VALUE;
            event.setAmount(scaled);
        }
    }

    // NeoForge 26.1.2.29+ moved BlockEvent.BreakEvent out to a top-level
    // BreakBlockEvent in the new event/level/block/ subpackage. Same API
    // (getPlayer / getLevel / getPos / getState) since it still extends
    // BlockEvent — only the type reference needed updating. NeoForge 26.1.2.0
    // through ~.28 still had the inner-class form; this build targets the
    // current beta line.
    @SubscribeEvent
    public static void onBlockBreak(BreakBlockEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;

        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.BLOCK_BREAK, event);

        var state = event.getState();
        BlockPos pos = event.getPos().immutable();

        // CropHarvestBonusPower (GitHub #91 fix layered in here):
        //   - Stripped logs are excluded entirely. The bonus is intended for
        //     chopping naturally-grown wood, not for un-shrinking the same
        //     block you stripped seconds ago.
        //   - Player-placed logs are tracked per-chunk and excluded — a
        //     builder placing a log wall and breaking it back down should not
        //     dupe their materials. World-gen logs and sapling-grown trees
        //     never get marked (TreeFeature places via FeaturePlaceContext,
        //     not the Entity place path), so they still earn the bonus.
        if (ActiveOriginService.has(sp, CropHarvestBonusPower.class, c -> true)) {
            boolean isMatureCrop = state.getBlock() instanceof CropBlock cb && cb.isMaxAge(state);
            boolean isLog = state.is(BlockTags.LOGS) && !isStrippedBlock(state);
            if (isMatureCrop || isLog) {
                boolean playerPlaced = isLog
                    && com.cyberday1.neoorigins.service.PlayerPlacedLogTracker.isPlaced(sl, pos);
                if (playerPlaced) {
                    // Player-placed log: clear the mark on break (the block is
                    // gone, no need to keep tracking) and skip the bonus.
                    com.cyberday1.neoorigins.service.PlayerPlacedLogTracker.clear(sl, pos);
                } else {
                    ItemStack tool = sp.getMainHandItem().copy();
                    sl.getServer().execute(() -> {
                        java.util.List<ItemStack> drops = Block.getDrops(state, sl, pos, null, sp, tool);
                        ActiveOriginService.forEachOfType(sp, CropHarvestBonusPower.class, cfg -> {
                            for (ItemStack drop : drops) {
                                for (int i = 0; i < cfg.extraDrops(); i++) {
                                    Block.popResource(sl, pos, drop.copy());
                                }
                            }
                        });
                    });
                }
            }
        }

        // TreeFellingPower — BFS upward from broken log
        if (state.is(BlockTags.LOGS) && !sp.isShiftKeyDown()) {
            if (ActiveOriginService.has(sp, TreeFellingPower.class, c -> true)) {
                final int[] maxBlocks = {64};
                ActiveOriginService.forEachOfType(sp, TreeFellingPower.class, cfg ->
                    maxBlocks[0] = Math.max(maxBlocks[0], cfg.maxBlocks()));
                fellTree(sl, pos, maxBlocks[0]);
            }
        }
    }

    private static void fellTree(ServerLevel level, BlockPos origin, int maxBlocks) {
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        // Check neighbors above and adjacent for connected logs
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                queue.add(origin.offset(dx, 1, dz));
            }
        }
        int broken = 0;
        while (!queue.isEmpty() && broken < maxBlocks) {
            BlockPos bp = queue.poll();
            if (!visited.add(bp)) continue;
            BlockState state = level.getBlockState(bp);
            if (!state.is(BlockTags.LOGS)) continue;
            level.destroyBlock(bp, true);
            broken++;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    queue.add(bp.offset(dx, 1, dz));
                }
            }
            // Also check same-y neighbors
            queue.add(bp.north());
            queue.add(bp.south());
            queue.add(bp.east());
            queue.add(bp.west());
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.BLOCK_PLACE, event);
    }

    @SubscribeEvent
    public static void onBabyEntitySpawn(BabyEntitySpawnEvent event) {
        if (!(event.getCausedByPlayer() instanceof ServerPlayer sp)) return;

        AgeableMob child = event.getChild();
        // BREED action — fires for any player-caused breed, independent of the
        // TwinBreeding power below. Dispatched first so action_on_event powers
        // see every breed event, not just twin-eligible ones.
        if (child != null) {
            com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(sp,
                com.cyberday1.neoorigins.service.EventPowerIndex.Event.BREED,
                new com.cyberday1.neoorigins.service.EventPowerIndex.EntityInteractContext(child, event));
        }

        if (!ActiveOriginService.has(sp, TwinBreedingPower.class, c -> true)) return;
        if (child == null) return;

        ActiveOriginService.forEachOfType(sp, TwinBreedingPower.class, cfg -> {
            if (sp.getRandom().nextFloat() < cfg.chance()) {
                var twin = (AgeableMob) child.getType().create(child.level(), EntitySpawnReason.BREEDING);
                if (twin != null) {
                    twin.setBaby(true);
                    twin.setPos(child.getX(), child.getY(), child.getZ());
                    child.level().addFreshEntity(twin);
                }
            }
        });
    }

    /**
     * TAME action — fires when a player successfully tames an animal
     * (AnimalTameEvent). Context is the tamed animal so action_on_event powers
     * can react to "player tamed X".
     */
    @SubscribeEvent
    public static void onAnimalTame(net.neoforged.neoforge.event.entity.living.AnimalTameEvent event) {
        if (!(event.getTamer() instanceof ServerPlayer sp)) return;
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(sp,
            com.cyberday1.neoorigins.service.EventPowerIndex.Event.TAME,
            new com.cyberday1.neoorigins.service.EventPowerIndex.EntityInteractContext(event.getAnimal(), event));
    }

    /**
     * Marks logs placed by a player in the per-chunk PlacedLogs attachment so
     * {@link CropHarvestBonusPower} can exclude them from the harvest bonus
     * later (GitHub #91). Sapling-grown trees and world-gen logs flow through
     * {@code TreeFeature}/{@code FeaturePlaceContext} which doesn't fire
     * {@code EntityPlaceEvent}, so they're never marked and still earn the
     * bonus as expected.
     */
    @SubscribeEvent
    public static void onPlayerPlaceLog(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player)) return;
        if (event.getLevel().isClientSide()) return;
        BlockState placed = event.getPlacedBlock();
        if (!placed.is(BlockTags.LOGS) || isStrippedBlock(placed)) return;
        // EntityPlaceEvent's level is a LevelAccessor on both branches; cast
        // is safe at this dispatch site (player placement always happens on
        // a real Level, never a ProtoChunk).
        if (!(event.getLevel() instanceof net.minecraft.world.level.Level level)) return;
        com.cyberday1.neoorigins.service.PlayerPlacedLogTracker.markPlaced(level, event.getPos().immutable());
    }

    /** True if the block's registry id starts with {@code stripped_} — covers
     *  every vanilla stripped-log variant and most mods that follow the same
     *  naming convention. Cheap registry lookup, no allocation. */
    private static boolean isStrippedBlock(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && id.getPath().startsWith("stripped_");
    }

    // ── neoorigins:muffle_sound ─────────────────────────────────────────────
    // A player holding muffle_sound stops emitting game-event vibrations, so
    // sculk sensors and wardens don't detect their footsteps, item use, etc.
    // VanillaGameEvent is cancelable and fires for every Level.gameEvent call
    // with the player as cause, which is exactly the emission a GameEventListener
    // (sculk / warden) would otherwise pick up.
    @SubscribeEvent
    public static void onMuffleGameEvent(VanillaGameEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getCause() instanceof ServerPlayer sp)) return;
        if (MuffleSoundPower.shouldMuffle(sp)) {
            event.setCanceled(true);
        }
    }
}
