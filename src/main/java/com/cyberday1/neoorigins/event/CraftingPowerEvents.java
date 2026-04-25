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
        // better_bone_meal moved to action_on_event (MOD_BONEMEAL_EXTRA).
        float chained = com.cyberday1.neoorigins.service.EventPowerIndex.dispatchModifier(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_BONEMEAL_EXTRA,
            event, 0f);
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
            // better_enchanting moved to action_on_event (MOD_ENCHANT_LEVEL).
            // Modifier is applied to the current level as the base.
            float chained = com.cyberday1.neoorigins.service.EventPowerIndex.dispatchModifier(
                sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_ENCHANT_LEVEL,
                event, (float) event.getEnchantLevel());
            int finalLevel = Math.max(1, Math.round(chained));
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

        // better_crafted_food moved to action_on_event (MOD_CRAFTED_FOOD_SATURATION).
        float bonus = com.cyberday1.neoorigins.service.EventPowerIndex.dispatchModifier(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_CRAFTED_FOOD_SATURATION,
            result, 0f);
        if (bonus <= 0f) return;

        FoodProperties.Builder builder = new FoodProperties.Builder()
            .nutrition(food.nutrition())
            .saturationModifier(food.saturation() + bonus);
        if (food.canAlwaysEat()) builder.alwaysEdible();
        result.set(DataComponents.FOOD, builder.build());
    }

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;

        // efficient_repairs moved to action_on_event (MOD_ANVIL_COST).
        float mult = com.cyberday1.neoorigins.service.EventPowerIndex.dispatchModifier(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_ANVIL_COST, event, 1.0f);
        if (mult != 1.0f) {
            int cost = Math.max(1, (int)(event.getCost() * mult));
            event.setCost(cost);
        }
    }
}
