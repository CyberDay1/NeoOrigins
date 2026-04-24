package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.builtin.*;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public class MovementPowerEvents {

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (ActiveOriginService.has(sp, PreventActionPower.class,
                config -> config.action() == PreventActionPower.Action.FALL_DAMAGE
                       && PreventActionPower.isGateOpen(sp, config))) {
            event.setCanceled(true);
        }
        if (sp.onClimbable() && ActiveOriginService.has(sp, ConditionalPower.class, config ->
                config.condition() == ConditionalPower.Condition.CLIMBING &&
                config.innerPower().getPath().contains("no_fall"))) {
            event.setCanceled(true);
        }
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.LAND, event.getDistance());
    }

    // BreakSpeedModifierPower used to be handled here via PlayerEvent.BreakSpeed,
    // but that event fires client-side for the local player and the
    // ServerPlayer guard silently filtered every call → mining speed never
    // applied. Powers now use the vanilla player.block_break_speed attribute,
    // which auto-syncs to the client. See BreakSpeedModifierPower.
    //
    // TODO: UnderwaterMiningSpeedPower is still broken for the same reason —
    // it has a positional condition (in water + airborne) that attribute
    // modifiers can't express, so it needs a client-aware fix.

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Projectile proj)) return;
        var owner = proj.getOwner();
        if (!(owner instanceof ServerPlayer sp)) return;
        if (!ActiveOriginService.has(sp, NoProjectileDivergencePower.class, c -> true)) return;

        // Recalculate trajectory with zero divergence — normalize existing delta motion
        Vec3 delta = proj.getDeltaMovement();
        if (delta.lengthSqr() > 0) {
            double speed = delta.length();
            Vec3 look = sp.getLookAngle().normalize().scale(speed);
            proj.setDeltaMovement(look);
        }
    }

    /**
     * Cobweb mining speed boost for Arachnid-like origins. Fires on both logical
     * sides — the cobweb_affinity capability is checked via whichever mirror is
     * available on the side handling the event.
     */
    @SubscribeEvent
    public static void onBreakSpeed(net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed event) {
        if (!event.getState().is(net.minecraft.world.level.block.Blocks.COBWEB)) return;
        var player = event.getEntity();
        boolean hasAffinity;
        if (player.level().isClientSide()) {
            hasAffinity = com.cyberday1.neoorigins.client.ClientActivePowers.hasCapability("cobweb_affinity");
        } else if (player instanceof ServerPlayer sp) {
            hasAffinity = ActiveOriginService.hasCapability(sp, "cobweb_affinity");
        } else {
            return;
        }
        if (hasAffinity) {
            event.setNewSpeed(event.getNewSpeed() * 10f);
        }
    }

    @SubscribeEvent
    public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack item = event.getItem();
        // food_restriction moved to action_on_event (FOOD_EATEN). Publish the
        // cancellable event itself as the dispatch target (so cancel_event
        // works via ICancellableEvent) while also stashing a FoodContext on
        // the ActionContextHolder so neoorigins:food_item_in_tag can read the
        // held stack.
        if (item.has(net.minecraft.core.component.DataComponents.FOOD)) {
            com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
                sp,
                com.cyberday1.neoorigins.service.EventPowerIndex.Event.FOOD_EATEN,
                new com.cyberday1.neoorigins.service.EventPowerIndex.FoodContext(item, event));
            // If a food_restriction power cancelled the eat, surface it to the
            // player — otherwise clicking to eat appears to do nothing (tester
            // report: "can be mistaken for lag" on Vampire Blood Diet). The
            // actionbar is less intrusive than chat and disappears on its own.
            if (event.isCanceled()) {
                sp.sendSystemMessage(
                    net.minecraft.network.chat.Component.translatable(
                        "message.neoorigins.food_restriction.cannot_eat")
                        .withStyle(net.minecraft.ChatFormatting.RED),
                    true);
            }
        }
    }
}
