package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.service.JumpActionRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/**
 * Fires {@link JumpActionRegistry} actions when a player jumps. The Apoli
 * {@code origins:modify_jump} power can declare an {@code entity_action}
 * that runs every jump; this handler is the dispatch point.
 *
 * <p>Subscribes to {@link LivingEvent.LivingJumpEvent}, which NeoForge
 * fires from {@code LivingEntity#jumpFromGround()} for any living entity.
 * We filter to {@code ServerPlayer} since the registry is player-keyed.
 */
@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public final class JumpEventHandler {

    private JumpEventHandler() {}

    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        JumpActionRegistry.fire(sp);
    }
}
