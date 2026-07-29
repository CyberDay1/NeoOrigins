package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.builtin.*;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
        // BONEMEAL action trigger — distinct from MOD_BONEMEAL_EXTRA (which
        // only scales the extra-application count). Fires the generic action
        // with the bonemealed block as context so action_on_event powers can
        // react to "player used bone meal here".
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(sp,
            com.cyberday1.neoorigins.service.EventPowerIndex.Event.BONEMEAL,
            new com.cyberday1.neoorigins.service.EventPowerIndex.BlockInteractContext(
                pos, sl.getBlockState(pos), event));
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
        applyCraftAmountBonus(sp, event.getCrafting());
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(sp,
            com.cyberday1.neoorigins.service.EventPowerIndex.Event.CRAFT_ITEM,
            new com.cyberday1.neoorigins.service.EventPowerIndex.CraftContext(event.getCrafting()));
    }

    @SubscribeEvent
    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        boostFoodIfCook(sp, event.getSmelting());
        applySmokingExpertBonus(sp, event.getSmelting());
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(sp,
            com.cyberday1.neoorigins.service.EventPowerIndex.Event.SMELT_ITEM,
            new com.cyberday1.neoorigins.service.EventPowerIndex.CraftContext(event.getSmelting()));
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
            // Re-read FOOD in case boostFoodIfCook already rebuilt this stack.
            FoodProperties current = result.get(DataComponents.FOOD);
            if (current == null) return;
            result.set(DataComponents.FOOD, rebuildFood(current,
                current.nutrition() + bonusNutrition,
                current.saturation() + config.multiplier() * 0.25f));
        });
    }

    /**
     * Rebuild {@link FoodProperties} with a new nutrition/saturation pair while
     * preserving {@code canAlwaysEat}. In 26.1, FoodProperties is just
     * (nutrition, saturation, canAlwaysEat) — eat duration and consumable
     * effects moved out to the {@code Consumable} data component, which we
     * don't touch here so vanilla preserves it untouched on the stack.
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
        return new FoodProperties(nutrition, saturation, source.canAlwaysEat());
    }

    /**
     * Applies blacksmith quality attributes to freshly crafted items.
     * Only fires at craft time — items from enchanting tables, anvils, loot, or
     * trades are left untouched.
     *
     * <p>Public so {@link com.cyberday1.neoorigins.mixin.CraftingMenuCraftAmountMixin}
     * can apply the buff to the assembled result slot itself — covering the
     * shift-click take path, where vanilla {@code quickMoveStack} distributes the
     * result into the inventory BEFORE {@code ItemCraftedEvent} fires, so the
     * post-hoc event mutation would land on an already-merged stack and be lost.
     * The underlying {@link QualityEquipmentPower#onItemCrafted} is idempotent
     * (per-modifier strip + durability marker), so double-application via both the
     * event and this seam is safe.
     */
    public static void applyQualityAttributes(ServerPlayer sp, ItemStack result) {
        ActiveOriginService.forEachOfType(sp, QualityEquipmentPower.class,
            config -> QualityEquipmentPower.onItemCrafted(sp, result, config));
    }

    /**
     * Smithing-table upgrade taken — called from
     * {@link com.cyberday1.neoorigins.mixin.SmithingMenuTakeMixin}, since NeoForge
     * fires no event for smithing output. Vanilla smithing copies the source
     * item's components, so the absolute durability/attribute snapshots written by
     * the blacksmith quality power went stale on upgrade (GitHub #103): first
     * re-derive the carried-over quality data against the upgraded item's own base
     * stats, then let the upgrading player's quality power (if any) re-apply —
     * smithing upgrades are part of the power's documented surface.
     */
    public static void onSmithingTake(ServerPlayer sp, ItemStack baseInput, ItemStack result) {
        QualityEquipmentPower.onSmithingUpgrade(baseInput, result);
        applyQualityAttributes(sp, result);
    }

    /**
     * Generic result-slot interceptor — called from
     * {@link com.cyberday1.neoorigins.mixin.SlotOnTakeMixin} at HEAD of every
     * {@link net.minecraft.world.inventory.Slot#onTake}. Lets the blacksmith
     * quality power fire on data-driven modded workstations (e.g. Overgeared's
     * smithing anvils) whose result slots are not covered by
     * {@code PlayerEvent.ItemCraftedEvent} or {@code SmithingMenuTakeMixin}.
     *
     * <p>This runs on EVERY inventory take, so the guards are ordered
     * cheap&rarr;expensive and bail as early as possible:
     * <ol>
     *   <li>the taken stack is gear (shared {@link QualityEquipmentPower#isQualityEligible});</li>
     *   <li>the slot is a result slot ({@code !mayPlace(EMPTY)} — vanilla/Overgeared
     *       output slots reject placement);</li>
     *   <li>the player has at least one active quality power that opts a menu id in;</li>
     *   <li>the current menu's registry id is in that opt-in union.</li>
     * </ol>
     * Dedupe across matching configs is handled by the power's own idempotent
     * modifier markers, so re-applying per matching config is safe.
     */
    public static void onGenericResultTake(ServerPlayer sp,
                                           net.minecraft.world.inventory.Slot slot,
                                           ItemStack taken) {
        // (a) gear check — cheapest, filters out the overwhelming common case.
        if (!QualityEquipmentPower.isQualityEligible(taken)) return;
        // (b) result-slot check — output slots reject placement.
        if (slot.mayPlace(ItemStack.EMPTY)) return;
        // (c) gather the union of opted-in menu ids across active quality powers.
        java.util.Set<String> union = new java.util.HashSet<>();
        java.util.List<QualityEquipmentPower.Config> configs = new java.util.ArrayList<>();
        ActiveOriginService.forEachOfType(sp, QualityEquipmentPower.class, config -> {
            configs.add(config);
            union.addAll(config.interceptMenus());
        });
        if (union.isEmpty()) return;
        // (d) resolve the current menu type id. getType() throws
        // UnsupportedOperationException for typeless menus (e.g. the player's own
        // inventory menu), which fire this take path constantly — swallow it.
        net.minecraft.world.inventory.MenuType<?> type;
        try {
            type = sp.containerMenu.getType();
        } catch (UnsupportedOperationException ignored) {
            return;
        }
        var menuId = BuiltInRegistries.MENU.getKey(type);
        if (menuId == null || !union.contains(menuId.toString())) return;
        // Match — apply for each active config (idempotent markers dedupe).
        for (QualityEquipmentPower.Config config : configs) {
            QualityEquipmentPower.onItemCrafted(sp, taken, config);
        }
    }

    /**
     * Grants bonus copies of the crafted item for CraftAmountBonusPower holders.
     * Only fires on actual crafting events — no false positives from pickups/hoppers.
     */
    private static void applyCraftAmountBonus(ServerPlayer sp, ItemStack result) {
        ActiveOriginService.forEachOfType(sp, CraftAmountBonusPower.class, config -> {
            var itemOpt = BuiltInRegistries.ITEM.get(Identifier.parse(config.outputItem()));
            if (itemOpt.isEmpty()) return;
            var targetItem = itemOpt.get().value();
            if (!result.is(targetItem)) return;
            if (config.bonusCount() > 0) {
                sp.getInventory().add(new ItemStack(targetItem, config.bonusCount()));
            }
        });
    }

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;

        // efficient_repairs moved to action_on_event (MOD_ANVIL_COST).
        float mult = com.cyberday1.neoorigins.service.EventPowerIndex.dispatchModifier(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_ANVIL_COST, event, 1.0f);
        if (mult != 1.0f) {
            int cost = Math.max(1, (int)(event.getXpCost() * mult));
            event.setXpCost(cost);
        }
    }

    /**
     * ENCHANT_ITEM fires when the player applies enchantments at a table
     * (PlayerEnchantItemEvent — post-apply). Context is the freshly-enchanted
     * stack. Distinct from {@link #onEnchantmentLevelSet} which modifies the
     * level offered (MOD_ENCHANT_LEVEL) before the player commits.
     */
    @SubscribeEvent
    public static void onItemEnchanted(net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(sp,
            com.cyberday1.neoorigins.service.EventPowerIndex.Event.ENCHANT_ITEM,
            new com.cyberday1.neoorigins.service.EventPowerIndex.CraftContext(event.getEnchantedItem()));
    }

    /**
     * ANVIL_REPAIR fires when a player takes the repaired/combined output from
     * an anvil. In 26.1 the old {@code AnvilRepairEvent} was removed; the
     * post-craft hook is now {@code AnvilCraftEvent.Post}, whose {@code
     * getOutput()} carries the finished stack. Distinct from
     * {@link #onAnvilUpdate}, which only previews the XP cost.
     */
    @SubscribeEvent
    public static void onAnvilRepair(net.neoforged.neoforge.event.entity.player.AnvilCraftEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(sp,
            com.cyberday1.neoorigins.service.EventPowerIndex.Event.ANVIL_REPAIR,
            new com.cyberday1.neoorigins.service.EventPowerIndex.CraftContext(event.getOutput()));
    }
}
