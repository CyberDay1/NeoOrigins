package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.builtin.*;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public class MovementPowerEvents {

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (ActiveOriginService.has(sp, PreventActionPower.class,
                config -> config.action() == PreventActionPower.Action.FALL_DAMAGE)) {
            event.setCanceled(true);
        }
        if (sp.onClimbable() && ActiveOriginService.has(sp, ConditionalPower.class, config ->
                config.condition() == ConditionalPower.Condition.CLIMBING &&
                config.innerPower().getPath().contains("no_fall"))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ActiveOriginService.forEachOfType(sp, BreakSpeedModifierPower.class, cfg -> {
            boolean applies = cfg.blockTag().map(tagId -> {
                TagKey<Block> tag = TagKey.create(Registries.BLOCK, tagId);
                return event.getState().is(tag);
            }).orElse(true);
            if (applies) {
                // Defence-in-depth clamp against non-finite results
                // (see CombatPowerEvents.onLivingDamage for context).
                float scaled = event.getNewSpeed() * cfg.multiplier();
                if (!Float.isFinite(scaled)) scaled = Float.MAX_VALUE;
                event.setNewSpeed(scaled);
            }
        });
        if (ActiveOriginService.has(sp, UnderwaterMiningSpeedPower.class, c -> true)
                && sp.isInWater() && !sp.onGround()) {
            float scaled = event.getNewSpeed() * 5.0f;
            if (!Float.isFinite(scaled)) scaled = Float.MAX_VALUE;
            event.setNewSpeed(scaled);
        }
    }

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
