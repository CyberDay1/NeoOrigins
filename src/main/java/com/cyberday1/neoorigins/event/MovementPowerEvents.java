package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.builtin.*;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
            if (applies) event.setNewSpeed(event.getNewSpeed() * cfg.multiplier());
        });
        if (ActiveOriginService.has(sp, UnderwaterMiningSpeedPower.class, c -> true)
                && sp.isInWater() && !sp.onGround()) {
            event.setNewSpeed(event.getNewSpeed() * 5.0f);
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
