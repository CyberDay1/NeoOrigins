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
    }

    // BreakSpeedModifierPower and UnderwaterMiningSpeedPower used to be handled
    // here via PlayerEvent.BreakSpeed, but that event fires client-side for the
    // local player and the ServerPlayer guard silently filtered every call →
    // mining speed never applied. Both now use vanilla attributes
    // (player.block_break_speed and player.submerged_mining_speed respectively),
    // which auto-sync to the client.

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

    @SubscribeEvent
    public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack item = event.getItem();
        ActiveOriginService.forEachOfType(sp, FoodRestrictionPower.class, cfg -> {
            var tag = TagKey.create(Registries.ITEM, cfg.itemTag());
            boolean inTag = item.is(tag);
            boolean shouldBlock = cfg.isWhitelist() ? !inTag : inTag;
            if (shouldBlock) event.setCanceled(true);
        });
    }
}
