package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.builtin.*;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public class WorldPowerEvents {

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof ServerPlayer sp)) return;
        Identifier mobTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        if (mobTypeId == null) return;
        if (ActiveOriginService.has(sp, MobsIgnorePlayerPower.class,
                cfg -> cfg.entityTypes().isEmpty() || cfg.entityTypes().contains(mobTypeId))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        Mob mob = event.getEntity();
        for (ServerPlayer sp : sl.players()) {
            if (!ActiveOriginService.has(sp, NoMobSpawnsNearbyPower.class, c -> true)) continue;
            ActiveOriginService.forEachOfType(sp, NoMobSpawnsNearbyPower.class, cfg -> {
                if (sp.distanceTo(mob) > cfg.radius()) return;
                if (matchesSpawnCategory(cfg, mob)) event.setSpawnCancelled(true);
            });
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
        if (mult[0] != 1.0f) event.setAmount(event.getAmount() * mult[0]);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        if (!ActiveOriginService.has(sp, CropHarvestBonusPower.class, c -> true)) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;

        var state = event.getState();
        boolean isMatureCrop = state.getBlock() instanceof CropBlock cb && cb.isMaxAge(state);
        boolean isLog = state.is(BlockTags.LOGS);
        if (!isMatureCrop && !isLog) return;

        BlockPos pos = event.getPos().immutable();
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
