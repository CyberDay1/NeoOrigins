package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.attachment.EntityAttachments;
import com.cyberday1.neoorigins.service.PersonalSpace;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Rules of the {@code neoorigins:personal_space} dimension that are not the
 * key itself. Mob spawning is refused in {@code WorldPowerEvents#onFinalizeSpawn}.
 */
@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public final class PersonalSpaceEvents {

    private PersonalSpaceEvents() {}

    /** Any exit other than the key (portal, /tp, another mod) forgets the return point. */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getFrom() == PersonalSpace.DIMENSION && event.getEntity() instanceof ServerPlayer sp) {
            sp.removeData(EntityAttachments.personalSpaceReturn());
        }
    }

    /** No spawn point can be set inside the dimension, by bed, anchor or command. */
    @SubscribeEvent
    public static void onSetSpawn(PlayerSetSpawnEvent event) {
        if (event.getNewSpawn() != null && event.getSpawnLevel() == PersonalSpace.DIMENSION) {
            event.setCanceled(true);
        }
    }

    /** Beds do nothing inside: no sleep, no spawn point, no explosion. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCanSleep(CanPlayerSleepEvent event) {
        if (PersonalSpace.isPersonalSpace(event.getLevel())) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        PersonalSpace.tick(event.getServer());
    }
}
