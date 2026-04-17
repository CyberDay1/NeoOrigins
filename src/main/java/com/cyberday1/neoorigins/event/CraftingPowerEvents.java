package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.builtin.*;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
        // EnchantmentLevelSetEvent has no player ref — spatial query for nearby players
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        BlockPos pos = event.getPos();
        var nearby = sl.getEntitiesOfClass(ServerPlayer.class,
            new net.minecraft.world.phys.AABB(pos).inflate(8));
        for (ServerPlayer sp : nearby) {
            final int[] bonus = {0};
            ActiveOriginService.forEachOfType(sp, BetterEnchantingPower.class, cfg ->
                bonus[0] += cfg.bonusLevels());
            if (bonus[0] > 0) {
                event.setEnchantLevel(event.getEnchantLevel() + bonus[0]);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        boostFoodIfCook(sp, event.getCrafting());
    }

    @SubscribeEvent
    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        boostFoodIfCook(sp, event.getSmelting());
    }

    /**
     * One-shot food saturation boost for BetterCraftedFoodPower. Applied once
     * at craft/smelt time — no tick scanning, no identity-hash tracking, no
     * compound re-application.
     */
    private static void boostFoodIfCook(ServerPlayer sp, ItemStack result) {
        FoodProperties food = result.get(DataComponents.FOOD);
        if (food == null) return;

        final float[] bonus = {0f};
        ActiveOriginService.forEachOfType(sp, BetterCraftedFoodPower.class,
            cfg -> bonus[0] += cfg.saturationBonus());
        if (bonus[0] <= 0f) return;

        FoodProperties.Builder builder = new FoodProperties.Builder()
            .nutrition(food.nutrition())
            .saturationModifier(food.saturation() + bonus[0]);
        if (food.canAlwaysEat()) builder.alwaysEdible();
        result.set(DataComponents.FOOD, builder.build());
    }

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;

        final float[] mult = {1.0f};
        ActiveOriginService.forEachOfType(sp, EfficientRepairsPower.class, cfg ->
            mult[0] *= cfg.costMultiplier());
        if (mult[0] != 1.0f) {
            int cost = Math.max(1, (int)(event.getXpCost() * mult[0]));
            event.setXpCost(cost);
        }
    }
}
