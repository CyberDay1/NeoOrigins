package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.builtin.*;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.enchanting.EnchantmentLevelSetEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public class CraftingPowerEvents {

    @SubscribeEvent
    public static void onBonemeal(BonemealEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;

        ActiveOriginService.forEachOfType(sp, BetterBoneMealPower.class, cfg -> {
            BlockPos pos = event.getPos();
            for (int i = 0; i < cfg.extraApplications(); i++) {
                BlockState state = sl.getBlockState(pos);
                if (state.getBlock() instanceof BonemealableBlock bmb) {
                    if (bmb.isValidBonemealTarget(sl, pos, state)) {
                        bmb.performBonemeal(sl, sl.getRandom(), pos, state);
                    }
                }
            }
        });
    }

    @SubscribeEvent
    public static void onEnchantmentLevelSet(EnchantmentLevelSetEvent event) {
        // EnchantmentLevelSetEvent has no player ref — scan nearby players within 8 blocks
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        BlockPos pos = event.getPos();
        for (ServerPlayer sp : sl.players()) {
            if (sp.blockPosition().closerThan(pos, 8)) {
                final int[] bonus = {0};
                ActiveOriginService.forEachOfType(sp, BetterEnchantingPower.class, cfg ->
                    bonus[0] += cfg.bonusLevels());
                if (bonus[0] > 0) {
                    event.setEnchantLevel(event.getEnchantLevel() + bonus[0]);
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;

        final float[] mult = {1.0f};
        ActiveOriginService.forEachOfType(sp, EfficientRepairsPower.class, cfg ->
            mult[0] *= cfg.costMultiplier());
        if (mult[0] != 1.0f) {
            int cost = Math.max(1, (int)(event.getCost() * mult[0]));
            event.setCost(cost);
        }
    }
}
