package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Handles NeoForge events for compat powers that need event cancellation.
 * All conditions are pre-compiled at load time — no JSON parsing at event time.
 */
@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public class CompatEventPowers {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    // ---- prevent_item_use ----

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (shouldPreventItemUse(sp, event.getItem())) {
            event.setCanceled(true);
            return;
        }
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.ITEM_USE, event.getItem());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (shouldPreventItemUse(sp, event.getItem())) {
            event.setCanceled(true);
        }
    }

    private static boolean shouldPreventItemUse(ServerPlayer player, ItemStack stack) {
        var powers = CompatPlayerState.getPowers(player, CompatPlayerState.EventType.PREVENT_ITEM_USE);
        if (powers.isEmpty()) return false;

        for (var power : powers) {
            if (power.itemPredicate() == null || power.itemPredicate().test(stack)) {
                return true;
            }
        }
        return false;
    }

    // ---- restrict_armor ----

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!CompatPlayerState.hasPower(sp, CompatPlayerState.EventType.RESTRICT_ARMOR)) return;

        // Check every 10 ticks to avoid per-tick overhead
        if (sp.tickCount % 10 != 0) return;

        var powers = CompatPlayerState.getPowers(sp, CompatPlayerState.EventType.RESTRICT_ARMOR);
        for (var power : powers) {
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                ItemStack equipped = sp.getItemBySlot(slot);
                if (equipped.isEmpty()) continue;
                boolean restricted = power.armorPredicate() != null
                    ? power.armorPredicate().isRestricted(equipped, slot)
                    : true; // No predicate = restrict all armor
                if (restricted) {
                    // Force-unequip: move to inventory or drop
                    if (!sp.getInventory().add(equipped.copy())) {
                        sp.drop(equipped.copy(), false);
                    }
                    sp.setItemSlot(slot, ItemStack.EMPTY);
                }
            }
        }
    }

    // ---- prevent_sleep ----

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerSleep(CanPlayerSleepEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        // Only block vanilla beds — modded sleep blocks (Vampirism coffins,
        // etc.) should not be prevented by origins sleep restrictions.
        if (!(sp.level().getBlockState(event.getPos())
                .getBlock() instanceof net.minecraft.world.level.block.BedBlock)) return;
        var powers = CompatPlayerState.getPowers(sp, CompatPlayerState.EventType.PREVENT_SLEEP);
        if (powers.isEmpty()) return;

        for (var power : powers) {
            if (power.entityCondition() != null && !power.entityCondition().test(sp)) continue;
            // block_condition gates on the bed's position (e.g. height < 70)
            if (power.blockPredicate() != null && !power.blockPredicate().test(sp, event.getPos())) continue;
            event.setProblem(net.minecraft.world.entity.player.Player.BedSleepingProblem.OTHER_PROBLEM);
            // Height-gated sleep: tell the player why they can't sleep here
            if (power.blockPredicate() != null) {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "You need fresher air to sleep \u2014 try higher ground."));
            }
            return;
        }
    }

    // ---- prevent_block_use ----

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!CompatPlayerState.hasPower(sp, CompatPlayerState.EventType.PREVENT_BLOCK_USE)) return;

        var powers = CompatPlayerState.getPowers(sp, CompatPlayerState.EventType.PREVENT_BLOCK_USE);
        for (var power : powers) {
            if (power.blockPredicate() == null || power.blockPredicate().test(sp, event.getPos())) {
                event.setCanceled(true);
                return;
            }
        }
    }

    // ---- prevent_entity_use ----

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (CompatPlayerState.hasPower(sp, CompatPlayerState.EventType.PREVENT_ENTITY_USE)) {
            event.setCanceled(true);
        }
    }

    // ---- modify_food ----

    /**
     * Apply registered {@code origins:modify_food} modifiers when food is eaten.
     * Fires at HIGH priority so the food properties are modified before vanilla
     * processes the nutrition/saturation.
     *
     * <p>Each entry can optionally filter by item condition. Modifiers follow
     * Apoli's {@code food_modifier}/{@code saturation_modifier} semantics via
     * {@link OriginsModifierMath}.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onFoodEaten(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack stack = event.getItem();
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) return;

        var entries = ModifyFoodRegistry.getEntries(sp);
        if (entries.isEmpty()) return;

        int nutrition = food.nutrition();
        float saturation = food.saturation();
        boolean modified = false;

        for (var entry : entries) {
            // Check item condition if present
            if (entry.itemPredicate() != null && !entry.itemPredicate().test(stack)) continue;

            if (!entry.foodModifiers().isEmpty()) {
                nutrition = (int) Math.round(OriginsModifierMath.apply(nutrition, entry.foodModifiers()));
                modified = true;
            }
            if (!entry.saturationModifiers().isEmpty()) {
                saturation = (float) OriginsModifierMath.apply(saturation, entry.saturationModifiers());
                modified = true;
            }
        }

        if (modified) {
            nutrition = Math.max(0, nutrition);
            saturation = Math.max(0f, saturation);
            FoodProperties.Builder builder = new FoodProperties.Builder()
                .nutrition(nutrition)
                .saturationModifier(saturation);
            if (food.canAlwaysEat()) builder.alwaysEdible();
            stack.set(DataComponents.FOOD, builder.build());
        }
    }
}
