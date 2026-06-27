package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.builtin.EdibleItemPower;
import com.cyberday1.neoorigins.power.builtin.RestrictItemsPower;
import com.cyberday1.neoorigins.power.builtin.ModifyFoodNutritionPower;
import com.cyberday1.neoorigins.power.builtin.PreventActionPower;
import com.cyberday1.neoorigins.power.builtin.RareWanderingLootPower;
import com.cyberday1.neoorigins.power.builtin.RestrictArmorPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.service.EventPowerIndex;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dispatchers for extended interaction events added in 2.0:
 * BLOCK_USE, ENTITY_USE, ITEM_PICKUP, ITEM_USE_FINISH.
 */
@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public class InteractionPowerEvents {

    // Tracks (traderEntityId + playerUUID) pairs to avoid double-injecting
    // charisma trades on repeated interactions with the same trader.
    // Wandering traders despawn, so this set stays bounded naturally.
    private static final Set<Long> CHARISMA_INJECTED = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static long charismaKey(int traderId, java.util.UUID playerId) {
        return ((long) traderId << 32) ^ playerId.getLeastSignificantBits();
    }

    // Pre-eat food/saturation snapshot for modify_food_nutrition. Captured at
    // use-Start so the Finish handler can recompute the bar from the real
    // starting value rather than a blind post-eat delta (which broke when
    // vanilla clamped the bar at 20 — Discord report: food at max effect gave
    // nothing). Cleared on Finish/Stop so an interrupted eat never leaks.
    private record FoodBaseline(int foodLevel, float saturation) {}
    private static final Map<UUID, FoodBaseline> FOOD_BASELINE = new ConcurrentHashMap<>();

    /**
     * Charisma — inject master-tier trades into wandering traders for players
     * with the rare_wandering_loot power. Fires at HIGH priority so the
     * offers are in place before vanilla's mobInteract opens the trade screen.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCharismaTraderInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!(event.getTarget() instanceof WanderingTrader trader)) return;
        if (!ActiveOriginService.has(sp, RareWanderingLootPower.class, cfg -> true)) return;

        long key = charismaKey(trader.getId(), sp.getUUID());
        if (!CHARISMA_INJECTED.add(key)) return; // already injected for this pair

        RareWanderingLootPower.injectTrades(sp, trader);
    }

    @SubscribeEvent
    public static void onBlockUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        EventPowerIndex.dispatch(sp, EventPowerIndex.Event.BLOCK_USE,
            new EventPowerIndex.BlockInteractContext(event.getPos(),
                event.getLevel().getBlockState(event.getPos()), event));
    }

    @SubscribeEvent
    public static void onEntityUse(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!(event.getTarget() instanceof net.minecraft.world.entity.LivingEntity target)) return;
        // Pass the cancellable EntityInteract event through the context so
        // neoorigins:cancel_event can veto the interaction (Discord report:
        // cancel_event was a no-op on villager_interact because the context
        // never carried the event).
        EventPowerIndex.dispatch(sp, EventPowerIndex.Event.ENTITY_USE,
            new EventPowerIndex.EntityInteractContext(target, event));
        // VILLAGER_INTERACT is a narrower alias for right-clicking a villager
        // or wandering trader (AbstractVillager covers both). Fired after the
        // generic ENTITY_USE so a power can target either granularity.
        if (target instanceof net.minecraft.world.entity.npc.AbstractVillager) {
            EventPowerIndex.dispatch(sp, EventPowerIndex.Event.VILLAGER_INTERACT,
                new EventPowerIndex.EntityInteractContext(target, event));
        }
    }

    @SubscribeEvent
    public static void onTradeCompleted(net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        EventPowerIndex.dispatch(sp, EventPowerIndex.Event.TRADE_COMPLETED,
            new EventPowerIndex.TradeContext(event.getMerchantOffer()));
    }

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        EventPowerIndex.dispatch(sp, EventPowerIndex.Event.ITEM_PICKUP,
            event.getItemEntity().getItem());
    }

    /**
     * restrict_items use enforcement at the use-Start chokepoint. Cancellable
     * and broader than {@link #onRightClickItem}: it also catches uses that begin
     * without a RightClickItem (e.g. food/shield raise paths, items started
     * programmatically). The hand isn't on this event, so all of the holder's
     * hands are considered; the shared {@link RestrictItemsPower#blocksUse}
     * decision (with a null hand to mean "any") makes the call. Runs at HIGHEST so
     * the cancel lands before vanilla begins the use.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemUseGateStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack stack = event.getItem();
        if (stack.isEmpty()) return;
        final boolean[] blocked = {false};
        ActiveOriginService.forEachOfType(sp, RestrictItemsPower.class, cfg -> {
            if (RestrictItemsPower.blocksUse(sp, stack, null, cfg)) blocked[0] = true;
        });
        if (blocked[0]) {
            event.setCanceled(true);
            // Item use is client-predicted (shields raise locally before the server
            // round-trips); cancelling server-side alone leaves the client visually
            // "using" it. Snap the prediction back to the authoritative state.
            RestrictItemsPower.resyncUseState(sp);
        }
    }

    @SubscribeEvent
    public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!event.getItem().has(net.minecraft.core.component.DataComponents.FOOD)) return;
        // Only snapshot when the player actually has a nutrition-override power;
        // keeps the map empty for everyone else.
        if (!ActiveOriginService.has(sp, ModifyFoodNutritionPower.class, cfg -> true)) return;
        var fd = sp.getFoodData();
        FOOD_BASELINE.put(sp.getUUID(), new FoodBaseline(fd.getFoodLevel(), fd.getSaturationLevel()));
    }

    @SubscribeEvent
    public static void onItemUseStop(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof ServerPlayer sp) FOOD_BASELINE.remove(sp.getUUID());
    }

    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        // Re-apply the eaten food at its overridden nutrition value, computed
        // from the pre-eat baseline captured in onItemUseStart. Falls back to
        // leaving vanilla's result untouched if no baseline was recorded.
        if (event.getItem().has(net.minecraft.core.component.DataComponents.FOOD)) {
            FoodBaseline baseline = FOOD_BASELINE.remove(sp.getUUID());
            if (baseline != null) {
                ActiveOriginService.forEachOfType(sp, ModifyFoodNutritionPower.class, config -> {
                    if (ModifyFoodNutritionPower.matchesFilter(event.getItem(), config)) {
                        ModifyFoodNutritionPower.applyOverride(sp, event.getItem(),
                            config.nutrition(), baseline.foodLevel(), baseline.saturation());
                    }
                });
            }
        }
        EventPowerIndex.dispatch(sp, EventPowerIndex.Event.ITEM_USE_FINISH, event.getItem());
        if (event.getItem().has(net.minecraft.core.component.DataComponents.FOOD)) {
            EventPowerIndex.dispatch(sp, EventPowerIndex.Event.FOOD_FINISHED,
                new EventPowerIndex.FoodContext(event.getItem()));
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        // restrict_items use enforcement — cancel the right-click before any use
        // animation starts (covers shields, bows, totems-by-raise, thrown items,
        // and edible items below). Routed through the shared blocksUse decision.
        final boolean[] useBlocked = {false};
        ActiveOriginService.forEachOfType(sp, RestrictItemsPower.class, cfg -> {
            if (RestrictItemsPower.blocksUse(sp, stack, event.getHand(), cfg)) useBlocked[0] = true;
        });
        if (useBlocked[0]) {
            event.setCanceled(true);
            // Correct the client's predicted raise/use (see onItemUseGateStart).
            RestrictItemsPower.resyncUseState(sp);
            return;
        }
        // Walk the player's edible_item powers — first match starts the use
        // animation. Actual nutrition is applied in onEdibleUseFinish below
        // when the eat animation completes; this matches vanilla food UX
        // (animation, hunger gating, releasable mid-bite).
        final boolean[] starting = {false};
        ActiveOriginService.forEachOfType(sp, EdibleItemPower.class, cfg -> {
            if (starting[0]) return;
            if (!EdibleItemPower.matches(stack, cfg)) return;
            if (!cfg.alwaysEdible() && sp.getFoodData().getFoodLevel() >= 20) return;
            starting[0] = true;
        });
        if (starting[0]) {
            // The companion ItemStackEdibleDurationMixin returns 32 ticks for
            // any matching stack so startUsingItem actually plays the eating
            // animation instead of completing on the same tick.
            sp.startUsingItem(event.getHand());
            event.setCanceled(true);
        }
    }

    /**
     * Companion to {@link #onRightClickItem}: when the eat animation completes,
     * find the matching {@link EdibleItemPower} config and apply the actual
     * nutrition / saturation / shrink. Sound + ITEM_USE_FINISH dispatch fire
     * here so they line up with the bite landing rather than the click.
     *
     * <p>Important to gate on the matching power again — Finish events fire for
     * vanilla food too, and we'd double-eat if we applied unconditionally.
     */
    @SubscribeEvent
    public static void onEdibleUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack stack = event.getItem();
        if (stack.isEmpty()) return;
        // Skip vanilla-food items: vanilla's finishUsingItem already applies
        // their FoodProperties. We only handle the EdibleItemPower-promoted
        // ones (which by definition lack a FOOD component, otherwise we
        // wouldn't have needed to promote them).
        if (stack.has(net.minecraft.core.component.DataComponents.FOOD)) return;
        final EdibleItemPower.Config[] matched = {null};
        ActiveOriginService.forEachOfType(sp, EdibleItemPower.class, cfg -> {
            if (matched[0] != null) return;
            if (!EdibleItemPower.matches(stack, cfg)) return;
            if (!cfg.alwaysEdible() && sp.getFoodData().getFoodLevel() >= 20) return;
            matched[0] = cfg;
        });
        if (matched[0] == null) return;
        EdibleItemPower.Config cfg = matched[0];
        // Snapshot the stack before shrinking — if the player had exactly 1,
        // shrink empties it and downstream conditions (food_item_in_tag) fail.
        ItemStack snapshot = stack.copy();
        sp.getFoodData().eat(cfg.nutrition(), cfg.saturation());
        // Vanilla LivingEntity.completeUsingItem passes a *copy* of the used
        // stack into the Finish event (LivingEntity.java:3203), and only
        // replaces the player's hand if event.getResultStack() != useItem.
        // Mutating event.getItem() therefore does nothing — the shrink has
        // to go through setResultStack on a fresh copy. GitHub #93.
        ItemStack result = event.getResultStack().copy();
        result.shrink(1);
        event.setResultStack(result);
        cfg.consumeSound().ifPresent(soundId -> {
            var snd = BuiltInRegistries.SOUND_EVENT.getOptional(soundId);
            snd.ifPresent(s -> sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                s, SoundSource.PLAYERS, 1.0f, 1.0f));
        });
        EventPowerIndex.dispatch(sp, EventPowerIndex.Event.ITEM_USE_FINISH, snapshot);
        // Also dispatch FOOD_FINISHED with FoodContext so food_item_in_tag /
        // food_item_id conditions work for edible-item-promoted items (e.g.
        // Caveborn minerals, Skeleton bone meal).
        EventPowerIndex.dispatch(sp, EventPowerIndex.Event.FOOD_FINISHED,
            new EventPowerIndex.FoodContext(snapshot));
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack neu = event.getTo();
        if (neu.isEmpty()) return;
        final var slot = event.getSlot();
        final boolean[] rejected = {false};
        ActiveOriginService.forEachOfType(sp, RestrictArmorPower.class, cfg -> {
            if (RestrictArmorPower.isRestricted(neu, slot, cfg)) rejected[0] = true;
        });
        // PreventActionPower armor_equip / chestplate_equip — same eject-on-block
        // behaviour as RestrictArmorPower, gated by the per-slot booleans on Config
        // (or by the legacy chestplate_equip → CHEST mapping).
        ActiveOriginService.forEachOfType(sp, PreventActionPower.class, cfg -> {
            if (PreventActionPower.blocksArmorSlot(slot, cfg)
                    && PreventActionPower.isGateOpen(sp, cfg)) {
                rejected[0] = true;
            }
        });
        // restrict_items equip enforcement — the modular gate's prevent_equip
        // path. Mirrors the eject-on-block behaviour above via the shared
        // RestrictItemsPower.blocksEquip decision (slot scope + deny/allow-list +
        // whole-power condition all routed through the one helper).
        ActiveOriginService.forEachOfType(sp, RestrictItemsPower.class, cfg -> {
            if (RestrictItemsPower.blocksEquip(sp, neu, slot, cfg)) rejected[0] = true;
        });
        if (rejected[0]) {
            sp.setItemSlot(slot, ItemStack.EMPTY);
            if (!sp.getInventory().add(neu)) sp.drop(neu, false);
        }
    }
}
