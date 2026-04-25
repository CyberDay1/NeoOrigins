package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.builtin.EdibleItemPower;
import com.cyberday1.neoorigins.power.builtin.RestrictArmorPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.service.EventPowerIndex;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/**
 * Dispatchers for extended interaction events added in 2.0:
 * BLOCK_USE, ENTITY_USE, ITEM_PICKUP, ITEM_USE_FINISH.
 */
@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public class InteractionPowerEvents {

    @SubscribeEvent
    public static void onBlockUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        EventPowerIndex.dispatch(sp, EventPowerIndex.Event.BLOCK_USE,
            new EventPowerIndex.BlockInteractContext(event.getPos(),
                event.getLevel().getBlockState(event.getPos())));
    }

    @SubscribeEvent
    public static void onEntityUse(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!(event.getTarget() instanceof net.minecraft.world.entity.LivingEntity target)) return;
        EventPowerIndex.dispatch(sp, EventPowerIndex.Event.ENTITY_USE,
            new EventPowerIndex.EntityInteractContext(target));
    }

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        EventPowerIndex.dispatch(sp, EventPowerIndex.Event.ITEM_PICKUP,
            event.getItemEntity().getItem());
    }

    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        EventPowerIndex.dispatch(sp, EventPowerIndex.Event.ITEM_USE_FINISH, event.getItem());
        // Food-specific finish dispatch with FoodContext so food_item_in_tag /
        // food_item_id conditions can target the eaten stack. Distinct from
        // FOOD_EATEN (which fires at use-START for cancellable restrictions —
        // applying a bonus there lets the player keep the bonus by releasing
        // right-click before the eat animation completes).
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
        sp.getFoodData().eat(cfg.nutrition(), cfg.saturation());
        stack.shrink(1);
        cfg.consumeSound().ifPresent(soundId -> {
            var snd = BuiltInRegistries.SOUND_EVENT.getOptional(soundId);
            snd.ifPresent(s -> sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                s, SoundSource.PLAYERS, 1.0f, 1.0f));
        });
        EventPowerIndex.dispatch(sp, EventPowerIndex.Event.ITEM_USE_FINISH, stack);
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
        if (rejected[0]) {
            sp.setItemSlot(slot, ItemStack.EMPTY);
            if (!sp.getInventory().add(neu)) sp.drop(neu, false);
        }
    }
}
