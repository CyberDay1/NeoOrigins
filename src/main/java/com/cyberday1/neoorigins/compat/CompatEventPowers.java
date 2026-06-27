package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.server.level.ServerPlayer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
        // Wrap stack + the cancellable Start event in a FoodContext (the
        // established stack+event context shape) so neoorigins:cancel_event
        // can veto the use and item-stack conditions can still read the held
        // stack. Previously this passed the raw ItemStack, which nothing
        // consumed and which made cancel_event a no-op on item_use.
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.ITEM_USE,
            new com.cyberday1.neoorigins.service.EventPowerIndex.FoodContext(event.getItem(), event));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (shouldPreventItemUse(sp, event.getItem())) {
            event.setCanceled(true);
        }
    }

    /**
     * Instant-use coverage for ITEM_USE / prevent_item_use.
     *
     * <p>{@link LivingEntityUseItemEvent.Start} (the primary dispatch above)
     * only fires for items with a use duration — food, potions, bows, shields,
     * spyglasses, tridents. Instant-use items never start a use, so fireworks,
     * ender pearls, snowballs, eggs, splash potions and fire charges slipped
     * past both prevent_item_use and {@code action_on_event:item_use}.
     * {@link PlayerInteractEvent.RightClickItem} fires for the right-click of
     * those instant items (including the elytra firework boost), so we catch
     * them here. Gated on a zero use-duration so held-use items don't
     * double-dispatch through both hooks.
     *
     * <p>This is the air/hand right-click. A firework launched while aiming at
     * a block within reach goes through {@code useOn} → RightClickBlock
     * instead; {@link #onRightClickBlockUse} covers that path. The two
     * NeoForge events are mutually exclusive per click (the vanilla client
     * sends either a use-item-on-block or a use-item packet, never both), so
     * an instant item never double-dispatches across the pair.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack stack = event.getItemStack();
        if (stack.getUseDuration(sp) > 0) return; // held-use items fire via Start
        if (shouldPreventItemUse(sp, stack)) {
            event.setCanceled(true);
            return;
        }
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.ITEM_USE,
            new com.cyberday1.neoorigins.service.EventPowerIndex.FoodContext(stack, event));
    }

    /**
     * Block-aimed instant-use coverage — the {@code useOn} sibling of
     * {@link #onRightClickItem}. A firework (or fire charge, ender pearl, etc.)
     * activated while the crosshair is on a block within reach goes through
     * {@code Item.useOn} and fires {@link PlayerInteractEvent.RightClickBlock}
     * rather than RightClickItem, so without this hook those launches slip past
     * prevent_item_use and {@code action_on_event:item_use}. Same zero
     * use-duration gate so held-use items (which fire via Start) don't leak in,
     * and the held-item predicate in {@link #shouldPreventItemUse} keeps this
     * from cancelling ordinary block interactions (chests, doors) with
     * unrelated items.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlockUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || stack.getUseDuration(sp) > 0) return; // held-use items fire via Start
        if (shouldPreventItemUse(sp, stack)) {
            // Deny only the item's useOn (firework launch) — leave the block's
            // own interaction (chest, door) intact, so prevent_item_use on a
            // held item doesn't lock the player out of normal block use.
            event.setUseItem(net.minecraft.util.TriState.FALSE);
            return;
        }
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.ITEM_USE,
            new com.cyberday1.neoorigins.service.EventPowerIndex.FoodContext(stack, event));
    }

    private static boolean shouldPreventItemUse(ServerPlayer player, ItemStack stack) {
        var powers = CompatPlayerState.getPowers(player, CompatPlayerState.EventType.PREVENT_ITEM_USE);
        if (powers.isEmpty()) return false;

        for (var power : powers) {
            // Holder gate: skip if the power-level condition isn't satisfied.
            if (power.entityCondition() != null && !power.entityCondition().test(player)) continue;
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
            // Holder gate: skip if the power-level condition isn't satisfied.
            if (power.entityCondition() != null && !power.entityCondition().test(sp)) continue;
            if (power.blockPredicate() == null || power.blockPredicate().test(sp, event.getPos())) {
                // Deny only the block's own interaction (opening chests, beds,
                // furnaces, crafting tables, etc.) — NOT the held item's useOn.
                // A full setCanceled() denies useItem too, which silently blocks
                // block PLACEMENT (a BlockItem places via useOn). prevent_block_use
                // means "can't use blocks", not "can't place blocks" — placement
                // suppression is prevent_item_use's job. (Seer "Intangible Will"
                // was blocking all placement while astral via this path.)
                event.setUseBlock(net.minecraft.util.TriState.FALSE);
                return;
            }
        }
    }

    // ---- USE-key (right-click) tracking for key.use-bound active_self powers ----
    // Apoli's key.use active_self (e.g. the deanos Mage spells) is cast by
    // right-clicking. The server has no held-state for the use key on a plain/empty
    // hand, so we stamp the tick of every right-click interaction and let the
    // active_self onTick poll read it via CompatPlayerState.isUseKeyDown. We run at
    // HIGHEST and accept cancelled events so a right-click still counts as a cast
    // even when prevent_block_use / prevent_item_use suppresses the vanilla action.

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onUseKeyBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer sp) CompatPlayerState.recordUseKey(sp);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onUseKeyItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer sp) CompatPlayerState.recordUseKey(sp);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onUseKeyEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (event.getEntity() instanceof ServerPlayer sp) CompatPlayerState.recordUseKey(sp);
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
            // The Finish event fires AFTER vanilla has already applied the
            // original food to FoodData. We can't modify the stack — instead
            // correct FoodData directly by computing the delta.
            int newNutrition = Math.max(0, nutrition);
            float newSaturation = Math.max(0f, saturation);
            int nutritionDelta = newNutrition - food.nutrition();
            float saturationDelta = newSaturation - food.saturation();
            var foodData = sp.getFoodData();
            foodData.setFoodLevel(net.minecraft.util.Mth.clamp(
                foodData.getFoodLevel() + nutritionDelta, 0, 20));
            foodData.setSaturation(net.minecraft.util.Mth.clamp(
                foodData.getSaturationLevel() + saturationDelta,
                0.0F, (float) foodData.getFoodLevel()));
        }
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
            var holderOpt = recipeManager.byKey(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, entry.recipeId()));
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

    // ---- modify_harvest ----

    /** Per-player block tag predicates for modify_harvest (allow:true). */
    private static final Map<java.util.UUID, List<net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>>> HARVEST_TAGS =
        new java.util.concurrent.ConcurrentHashMap<>();

    public static void registerHarvestTag(ServerPlayer player, net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tag) {
        HARVEST_TAGS.computeIfAbsent(player.getUUID(), k -> Collections.synchronizedList(new java.util.ArrayList<>())).add(tag);
    }

    public static void unregisterHarvestTag(ServerPlayer player, net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tag) {
        var list = HARVEST_TAGS.get(player.getUUID());
        if (list != null) list.remove(tag);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHarvestCheck(net.neoforged.neoforge.event.entity.player.PlayerEvent.HarvestCheck event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (event.canHarvest()) return;

        var tags = HARVEST_TAGS.get(sp.getUUID());
        if (tags == null || tags.isEmpty()) return;

        for (var tag : tags) {
            if (event.getTargetBlock().is(tag)) {
                event.setCanHarvest(true);
                return;
            }
        }
    }

    // ---- prevent_game_event ----

    /** Per-player set of blocked game event Identifiers. */
    private static final Map<java.util.UUID, java.util.Set<net.minecraft.resources.Identifier>> BLOCKED_GAME_EVENTS = new java.util.concurrent.ConcurrentHashMap<>();

    public static void registerBlockedGameEvent(ServerPlayer player, net.minecraft.resources.Identifier eventId) {
        BLOCKED_GAME_EVENTS.computeIfAbsent(player.getUUID(), k -> java.util.concurrent.ConcurrentHashMap.newKeySet())
            .add(eventId);
    }

    public static void unregisterBlockedGameEvent(ServerPlayer player, net.minecraft.resources.Identifier eventId) {
        var set = BLOCKED_GAME_EVENTS.get(player.getUUID());
        if (set != null) {
            set.remove(eventId);
            if (set.isEmpty()) BLOCKED_GAME_EVENTS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onGameEvent(net.neoforged.neoforge.event.VanillaGameEvent event) {
        if (event.getCause() == null) return;
        if (!(event.getCause() instanceof ServerPlayer sp)) return;
        var blocked = BLOCKED_GAME_EVENTS.get(sp.getUUID());
        if (blocked == null || blocked.isEmpty()) return;
        var eventKey = event.getVanillaEvent().unwrapKey();
        if (eventKey.isPresent() && blocked.contains(eventKey.get().identifier())) {
            event.setCanceled(true);
        }
    }
}
