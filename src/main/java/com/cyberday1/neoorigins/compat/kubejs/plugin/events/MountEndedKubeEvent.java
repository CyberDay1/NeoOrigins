package com.cyberday1.neoorigins.compat.kubejs.plugin.events;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Fired when a player ends a mount-power ride — pressed the keybind to
 * toggle off, or the power was revoked while riding.
 *
 * <p>Note: vanilla dismount paths (jumping off, vehicle dying, etc.) are
 * not currently covered — only explicit dismounts via the mount power
 * trigger this event.
 *
 * <p>JS:
 * <pre>
 *   NeoOriginsEvents.mountEnded(event => {
 *     console.log(`${event.rider.name} dismounted ${event.vehicle.type}`)
 *   })
 * </pre>
 */
public class MountEndedKubeEvent implements KubeEvent {
    private final ServerPlayer rider;
    private final Entity vehicle;

    public MountEndedKubeEvent(ServerPlayer rider, Entity vehicle) {
        this.rider = rider;
        this.vehicle = vehicle;
    }

    public ServerPlayer getRider() { return rider; }
    public Entity getVehicle() { return vehicle; }
}
