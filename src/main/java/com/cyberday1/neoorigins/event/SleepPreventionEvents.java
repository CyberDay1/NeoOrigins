package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.power.builtin.PreventActionPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.service.SpawnHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;

/**
 * Handler for {@link PreventActionPower.Action#SLEEP} — blocks sleep on
 * {@code CanPlayerSleepEvent} while still setting the player's respawn
 * position so bed interaction can serve as a spawn anchor for sleepless
 * origins (phantom, vampire, etc.).
 *
 * <p>Vanilla's {@code Player.startSleepInBed} sets the respawn position
 * only after the can-sleep check passes. Merely blocking the event would
 * prevent the spawn set. This handler explicitly writes the respawn
 * position via {@link SpawnHelper} (cross-version wrapper) before setting
 * the problem, so the player gets the anchor benefit without the time-skip
 * / sleep animation.
 */
@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public final class SleepPreventionEvents {

    private SleepPreventionEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCanSleep(CanPlayerSleepEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!hasSleepPrevent(sp)) return;

        // Set respawn first via the cross-version helper, so sleepless
        // origins still get the bed-anchor benefit even though the actual
        // sleep is blocked below.
        SpawnHelper.setBedSpawn(sp, sp.level().dimension(), event.getPos(),
            sp.getYRot(), true, true);

        event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
        sp.sendSystemMessage(Component.literal("You don't need sleep — but you remember this place."));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean hasSleepPrevent(ServerPlayer sp) {
        for (PowerHolder<?> holder : ActiveOriginService.allPowers(sp)) {
            if (!(holder.type() instanceof PreventActionPower)) continue;
            PreventActionPower.Config config = (PreventActionPower.Config) holder.config();
            if (config.action() == PreventActionPower.Action.SLEEP
                && PreventActionPower.isGateOpen(sp, config)) {
                return true;
            }
        }
        return false;
    }
}
