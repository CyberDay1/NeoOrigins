package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.builtin.PreventActionPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Runs {@link PreventActionPower#enforceMovementBlock} for SWIM and ELYTRA
 * holders on {@link PlayerTickEvent.Post}. Separate from the main tick
 * handler because the .Post phase fires *after* vanilla's travel() and
 * swim-flag update — which is the only point at which our reset can
 * actually stick.
 *
 * <p>Pulled out of {@link PreventActionPower#onTick} (which is invoked
 * from the .Pre phase via {@code holder.onTick}) when the Earth Mage
 * can't-swim ability was reported as a no-op (v2.1, 2026-05-25).
 */
@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public final class PreventActionPostTickHandler {

    private PreventActionPostTickHandler() {}

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ActiveOriginService.forEachOfType(sp, PreventActionPower.class,
            cfg -> PreventActionPower.enforceMovementBlock(sp, cfg));
    }
}
