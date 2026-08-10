package com.cyberday1.neoorigins.compat.kubejs.plugin.events;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Fired after a player has successfully started riding a target via the
 * mount power — covers both mob mounts (vehicle is any LivingEntity) and
 * player mounts (vehicle is a ServerPlayer that consented).
 *
 * <p>JS:
 * <pre>
 *   NeoOriginsEvents.mountStarted(event => {
 *     console.log(`${event.rider.name} mounted ${event.vehicle.type} (${event.position})`)
 *   })
 * </pre>
 */
public class MountStartedKubeEvent implements KubeEvent {
    private final ServerPlayer rider;
    private final LivingEntity vehicle;
    private final String position;

    public MountStartedKubeEvent(ServerPlayer rider, LivingEntity vehicle, String position) {
        this.rider = rider;
        this.vehicle = vehicle;
        this.position = position;
    }

    public ServerPlayer getRider() { return rider; }
    public LivingEntity getVehicle() { return vehicle; }
    /** Either {@code "centered"} or {@code "shoulder"}. */
    public String getPosition() { return position; }
}
