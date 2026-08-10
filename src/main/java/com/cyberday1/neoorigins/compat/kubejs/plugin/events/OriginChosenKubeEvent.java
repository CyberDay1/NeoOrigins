package com.cyberday1.neoorigins.compat.kubejs.plugin.events;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.Identifier;
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
    private final Identifier layerId;
    private final Identifier originId;

    public OriginChosenKubeEvent(ServerPlayer player, Identifier layerId, Identifier originId) {
        this.player = player;
        this.layerId = layerId;
        this.originId = originId;
    }

    public ServerPlayer getPlayer() { return player; }
    public Identifier getLayerId() { return layerId; }
    public Identifier getOriginId() { return originId; }
}
