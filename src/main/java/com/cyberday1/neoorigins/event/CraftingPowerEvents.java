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

        BlockPos pos = event.getPos();
        // Legacy class scan.
        final int[] extras = {0};
        ActiveOriginService.forEachOfType(sp, BetterBoneMealPower.class,
            cfg -> extras[0] += cfg.extraApplications());
        // 2.0: chain any action_on_event powers declared for MOD_BONEMEAL_EXTRA.
        float chained = com.cyberday1.neoorigins.service.EventPowerIndex.dispatchModifier(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_BONEMEAL_EXTRA,
            event, (float) extras[0]);
        int total = Math.max(0, Math.round(chained));
        for (int i = 0; i < total; i++) {
            BlockState state = sl.getBlockState(pos);
            if (state.getBlock() instanceof BonemealableBlock bmb) {
                if (bmb.isValidBonemealTarget(sl, pos, state)) {
                    bmb.performBonemeal(sl, sl.getRandom(), pos, state);
                }
            }
        }
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
            // 2.0: chain any action_on_event powers declared for MOD_ENCHANT_LEVEL.
            // Modifier is applied to the current level as the base; chain converts
            // int ↔ float at the boundary.
            float chained = com.cyberday1.neoorigins.service.EventPowerIndex.dispatchModifier(
                sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_ENCHANT_LEVEL,
                event, (float) event.getEnchantLevel());
            int finalLevel = Math.max(1, Math.round(chained)) + bonus[0];
            if (finalLevel != event.getEnchantLevel()) {
                event.setEnchantLevel(finalLevel);
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
        // 2.0: chain any action_on_event powers declared for MOD_CRAFTED_FOOD_SATURATION.
        bonus[0] = com.cyberday1.neoorigins.service.EventPowerIndex.dispatchModifier(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_CRAFTED_FOOD_SATURATION,
            result, bonus[0]);
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
        // 2.0: chain any action_on_event powers declared for MOD_ANVIL_COST.
        mult[0] = com.cyberday1.neoorigins.service.EventPowerIndex.dispatchModifier(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_ANVIL_COST, event, mult[0]);
        if (mult[0] != 1.0f) {
            int cost = Math.max(1, (int)(event.getXpCost() * mult[0]));
            event.setXpCost(cost);
        }
    }
}
