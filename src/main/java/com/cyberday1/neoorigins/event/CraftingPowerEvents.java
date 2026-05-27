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
        applyQualityAttributes(sp, event.getCrafting());
    }

    @SubscribeEvent
    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        boostFoodIfCook(sp, event.getSmelting());
        applySmokingExpertBonus(sp, event.getSmelting());
    }


    /**
     * One-shot food boost for Cook class. Adds bonus saturation AND nutrition
     * at craft/smelt time — no tick scanning, no identity-hash tracking.
     */
    private static void boostFoodIfCook(ServerPlayer sp, ItemStack result) {
        FoodProperties food = result.get(DataComponents.FOOD);
        if (food == null) return;

        float satBonus = com.cyberday1.neoorigins.service.EventPowerIndex.dispatchModifier(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_CRAFTED_FOOD_SATURATION,
            result, 0f);
        if (satBonus <= 0f) return;

        // Add +1 nutrition (hunger bar) alongside the saturation bonus
        int bonusNutrition = 1;
        result.set(DataComponents.FOOD, rebuildFood(food,
            food.nutrition() + bonusNutrition,
            food.saturation() + satBonus));
    }

    /**
     * Smoking Expert — extra nutrition for food cooked in a smoker/furnace.
     * Stacks with the Cook's base food boost from boostFoodIfCook.
     */
    private static void applySmokingExpertBonus(ServerPlayer sp, ItemStack result) {
        FoodProperties food = result.get(DataComponents.FOOD);
        if (food == null) return;

        ActiveOriginService.forEachOfType(sp, MoreSmokerXpPower.class, config -> {
            int bonusNutrition = Math.round(config.multiplier());
            if (bonusNutrition <= 0) return;
            // forEachOfType iterates the current food snapshot — re-read in case
            // boostFoodIfCook already rebuilt FOOD on this stack.
            FoodProperties current = result.get(DataComponents.FOOD);
            if (current == null) return;
            result.set(DataComponents.FOOD, rebuildFood(current,
                current.nutrition() + bonusNutrition,
                current.saturation() + config.multiplier() * 0.25f));
        });
    }

    /**
     * Rebuild {@link FoodProperties} with a new nutrition/saturation pair while
     * preserving every other field on the source (eatSeconds, usingConvertsTo,
     * effects, canAlwaysEat).
     *
     * <p>Constructs the {@link FoodProperties} record directly rather than going
     * through {@link FoodProperties.Builder}. The builder's
     * {@code saturationModifier(float)} is misleadingly named: it stores a
     * <em>multiplier</em>, and {@code build()} runs it through
     * {@code FoodConstants.saturationByModifier(nutrition, modifier)} which
     * returns {@code nutrition * modifier * 2.0}. Mollan-reported: cooked steak
     * came out with 2639 saturation because each Cook+SmokingExpert pass fed
     * the previous (already-blown-up) saturation back through the multiplier.
     * Constructing the record directly lets the {@code saturation} parameter
     * remain a literal absolute value, matching the Apoli/Origins semantics.
     */
    private static FoodProperties rebuildFood(FoodProperties source, int nutrition, float saturation) {
        return new FoodProperties(
            nutrition,
            saturation,
            source.canAlwaysEat(),
            source.eatSeconds(),
            source.usingConvertsTo(),
            source.effects()
        );
    }

    /**
     * Applies blacksmith quality attributes to freshly crafted items.
     * Only fires at craft time — items from enchanting tables, anvils, loot, or
     * trades are left untouched.
     */
    private static void applyQualityAttributes(ServerPlayer sp, ItemStack result) {
        ActiveOriginService.forEachOfType(sp, QualityEquipmentPower.class,
            config -> QualityEquipmentPower.onItemCrafted(sp, result, config));
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
