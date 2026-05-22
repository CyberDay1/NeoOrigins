package com.cyberday1.neoorigins.compat.kubejs.plugin.events;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fired when a player picks an origin for a layer for the first time
 * (no prior origin on that layer). For any-time origin changes, listen
 * to {@link OriginChangedKubeEvent} instead.
 *
 * <p>JS:
 * <pre>
 *   NeoOriginsEvents.originChosen(event => {
 *     console.log(`${event.player.name} chose ${event.originId} on layer ${event.layerId}`)
 *   })
 * </pre>
 */
public class OriginChosenKubeEvent implements KubeEvent {
    private final ServerPlayer player;
    private final ResourceLocation layerId;
    private final ResourceLocation originId;

    public OriginChosenKubeEvent(ServerPlayer player, ResourceLocation layerId, ResourceLocation originId) {
        this.player = player;
        this.layerId = layerId;
        this.originId = originId;
    }

    public ServerPlayer getPlayer() { return player; }
    public ResourceLocation getLayerId() { return layerId; }
    public ResourceLocation getOriginId() { return originId; }
}
