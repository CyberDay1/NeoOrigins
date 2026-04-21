package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.builtin.RestrictArmorPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.service.EventPowerIndex;
import net.minecraft.server.level.ServerPlayer;
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
 *
 * <p>Each handler is a thin pass-through to {@link EventPowerIndex#dispatch}
 * with the appropriate context payload. Pack-author conditions can then read
 * the active context via {@code ActionContextHolder}.
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
