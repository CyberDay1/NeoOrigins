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

    // ---- modify_xp_gain ----

    /**
     * Apply registered {@code modify_xp_gain} modifiers to incoming XP.
     * {@code PlayerXpEvent.XpChange} fires for every XP increment — orb
     * pickup, command grants, anvil refunds — so a single hook covers
     * all sources Apoli's verb is expected to affect.
     *
     * <p>The combined modifier is computed via
     * {@link com.cyberday1.neoorigins.compat.OriginsModifierMath}, then
     * the integer amount is rounded with a floor at 0 so a fully-zeroing
     * modifier doesn't become accidentally negative.
     */
    @SubscribeEvent
    public static void onXpChange(net.neoforged.neoforge.event.entity.player.PlayerXpEvent.XpChange event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        int original = event.getAmount();
        if (original <= 0) return;
        double modified = NumericModifierRegistry.apply(sp, NumericModifierRegistry.Kind.XP_GAIN, original);
        int rounded = Math.max(0, (int) Math.round(modified));
        if (rounded != original) event.setAmount(rounded);
    }

    // ---- modify_crafting ----

    /**
     * When the player completes a craft, check whether any of their active
     * {@code modify_crafting} entries target the recipe just used. If so,
     * empty the original result stack and give the player the configured
     * replacement instead.
     *
     * <p>Recipe match works by iterating each registered entry and asking
     * the recipe manager whether {@code recipeManager.byKey(entry.recipeId)}
     * matches the {@link net.minecraft.world.item.crafting.CraftingInput}
     * built from the event's container. We do this rather than calling
     * {@code recipeManager.getRecipeFor} once because Apoli's
     * {@code modify_crafting} can target any recipe by id — including
     * pack-defined ones — and there's typically only a handful of
     * registered overrides per player.
     */
    @SubscribeEvent
    public static void onItemCrafted(net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!(event.getInventory() instanceof net.minecraft.world.inventory.CraftingContainer cc)) return;

        var entries = ModifyCraftingRegistry.getEntries(sp);
        if (entries.isEmpty()) return;

        // Build a CraftingInput once and reuse for each entry's recipe match.
        int width = cc.getWidth();
        int height = cc.getHeight();
        java.util.List<ItemStack> items = new java.util.ArrayList<>(cc.getContainerSize());
        for (int i = 0; i < cc.getContainerSize(); i++) items.add(cc.getItem(i));
        var input = net.minecraft.world.item.crafting.CraftingInput.of(width, height, items);

        var recipeManager = sp.level().getServer().getRecipeManager();

        for (var entry : entries) {
            var holderOpt = recipeManager.byKey(entry.recipeId());
            if (holderOpt.isEmpty()) continue;
            var recipe = holderOpt.get().value();
            if (!(recipe instanceof net.minecraft.world.item.crafting.CraftingRecipe craftingRecipe)) continue;
            if (!craftingRecipe.matches(input, sp.level())) continue;

            // Build the replacement stack: resolve item, set count, run
            // legacy SNBT through the bridge for Potion/Enchantments/etc.
            var itemOpt = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(entry.replacementItem());
            if (itemOpt.isEmpty()) {
                NeoOrigins.LOGGER.warn("[CompatB] modify_crafting: replacement item '{}' not in registry — skipped",
                    entry.replacementItem());
                continue;
            }
            ItemStack replacement = new ItemStack(itemOpt.get(), entry.replacementCount());
            if (!entry.replacementTag().isEmpty()) {
                LegacyTagToComponents.applySnbt(replacement, entry.replacementTag(), sp.registryAccess());
            }

            // Empty what the player just crafted; vanilla finishes the take
            // by removing this stack-shaped result so setCount(0) is the
            // canonical "consume the original" gesture. Then add the
            // replacement to the player's inventory.
            event.getCrafting().setCount(0);
            sp.getInventory().add(replacement);
            return;
        }
    }
}
