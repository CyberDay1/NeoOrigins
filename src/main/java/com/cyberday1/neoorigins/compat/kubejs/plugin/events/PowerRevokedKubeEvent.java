package com.cyberday1.neoorigins.compat.kubejs.plugin.events;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fired after a power has been revoked from a player — typically as part
 * of an origin change away from the origin that granted the power.
 *
 * <p>JS:
 * <pre>
 *   NeoOriginsEvents.powerRevoked(event => {
 *     console.log(`${event.player.name} lost ${event.powerId}`)
 *   })
 * </pre>
 */
public class PowerRevokedKubeEvent implements KubeEvent {
    private final ServerPlayer player;
    private final ResourceLocation powerId;

    public PowerRevokedKubeEvent(ServerPlayer player, ResourceLocation powerId) {
        this.player = player;
        this.powerId = powerId;
    }

    public ServerPlayer getPlayer() { return player; }
    public ResourceLocation getPowerId() { return powerId; }
}
