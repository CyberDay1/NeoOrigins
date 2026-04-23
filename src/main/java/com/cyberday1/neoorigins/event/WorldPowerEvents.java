package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.builtin.*;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.service.MinionTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
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

import java.util.*;

@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public class WorldPowerEvents {

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof ServerPlayer sp)) return;

        // A tracked minion can never target its own summoner. Covers every
        // minion source that routes through MinionTracker (summon_minion,
        // tame_mob, and any future additions).
        if (MinionTracker.summonerOf(event.getEntity())
                .filter(summoner -> summoner.getUUID().equals(sp.getUUID()))
                .isPresent()) {
            event.setCanceled(true);
            return;
        }

        ResourceLocation mobTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        if (mobTypeId == null) return;
        if (ActiveOriginService.has(sp, MobsIgnorePlayerPower.class,
                cfg -> cfg.entityTypes().isEmpty() || cfg.entityTypes().contains(mobTypeId))) {
            // Retaliation window: if the player recently hit this mob, allow
            // targeting so the mob can fight back. Vanilla clears
            // getLastHurtByMob() on its own timer; we just defer to it.
            if (event.getEntity().getLastHurtByMob() == sp) return;
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
        if (mob.getType().getCategory() != MobCategory.MONSTER) return;
        for (ServerPlayer sp : sl.players()) {
            ActiveOriginService.forEachOfType(sp, NoMobSpawnsNearbyPower.class, cfg -> {
                if (sp.distanceTo(mob) <= cfg.radius() && matchesSpawnCategory(cfg, mob)) {
                    event.setSpawnCancelled(true);
                }
            });
            if (event.isSpawnCancelled()) break;
        }
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
        final float[] mult = {1.0f};
        ActiveOriginService.forEachOfType(sp, NaturalRegenModifierPower.class,
            cfg -> mult[0] *= cfg.multiplier());
        if (mult[0] != 1.0f) {
            // Defence-in-depth clamp against non-finite results — see
            // CombatPowerEvents.onLivingDamage for the full story of
            // how an unclamped multiply can brick a save via NaN health.
            float scaled = event.getAmount() * mult[0];
            if (!Float.isFinite(scaled)) scaled = Float.MAX_VALUE;
            event.setAmount(scaled);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;

        var state = event.getState();
        BlockPos pos = event.getPos().immutable();

        // CropHarvestBonusPower
        if (ActiveOriginService.has(sp, CropHarvestBonusPower.class, c -> true)) {
            boolean isMatureCrop = state.getBlock() instanceof CropBlock cb && cb.isMaxAge(state);
            boolean isLog = state.is(BlockTags.LOGS);
            if (isMatureCrop || isLog) {
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
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos seed = origin.offset(dx, 1, dz);
                if (visited.add(seed)) queue.add(seed);
            }
        }
        int broken = 0;
        while (!queue.isEmpty() && broken < maxBlocks) {
            BlockPos bp = queue.poll();
            BlockState state = level.getBlockState(bp);
            if (!state.is(BlockTags.LOGS)) continue;
            level.destroyBlock(bp, true);
            broken++;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos neighbor = bp.offset(dx, 1, dz);
                    if (visited.add(neighbor)) queue.add(neighbor);
                }
            }
            BlockPos n;
            n = bp.north(); if (visited.add(n)) queue.add(n);
            n = bp.south(); if (visited.add(n)) queue.add(n);
            n = bp.east();  if (visited.add(n)) queue.add(n);
            n = bp.west();  if (visited.add(n)) queue.add(n);
        }
    }

    @SubscribeEvent
    public static void onBabyEntitySpawn(BabyEntitySpawnEvent event) {
        if (!(event.getCausedByPlayer() instanceof ServerPlayer sp)) return;
        if (!ActiveOriginService.has(sp, TwinBreedingPower.class, c -> true)) return;

        AgeableMob child = event.getChild();
        if (child == null) return;

        ActiveOriginService.forEachOfType(sp, TwinBreedingPower.class, cfg -> {
            if (sp.getRandom().nextFloat() < cfg.chance()) {
                var twin = (AgeableMob) child.getType().create(child.level());
                if (twin != null) {
                    twin.setBaby(true);
                    twin.setPos(child.getX(), child.getY(), child.getZ());
                    child.level().addFreshEntity(twin);
                }
            }
        });
    }
}
