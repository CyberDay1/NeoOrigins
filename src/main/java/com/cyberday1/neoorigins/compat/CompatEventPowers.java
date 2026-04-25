package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
        var powers = CompatPlayerState.getPowers(sp, CompatPlayerState.EventType.PREVENT_SLEEP);
        if (powers.isEmpty()) return;

        for (var power : powers) {
            if (power.entityCondition() != null && !power.entityCondition().test(sp)) continue;
            event.setProblem(net.minecraft.world.entity.player.Player.BedSleepingProblem.OTHER_PROBLEM);
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
}
