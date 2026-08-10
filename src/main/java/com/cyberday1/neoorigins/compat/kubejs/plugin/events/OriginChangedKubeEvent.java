package com.cyberday1.neoorigins.compat.kubejs.plugin.events;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fired whenever a player's active origin on a layer changes — including
 * the first selection (oldOriginId is null), admin /set, or reset.
 *
 * <p>JS:
 * <pre>
 *   NeoOriginsEvents.originChanged(event => {
 *     console.log(`${event.player.name}: ${event.oldOriginId} -> ${event.newOriginId}`)
 *   })
 * </pre>
 */
public class OriginChangedKubeEvent implements KubeEvent {
    private final ServerPlayer player;
    private final Identifier layerId;
    private final Identifier oldOriginId;
    private final Identifier newOriginId;

    public OriginChangedKubeEvent(ServerPlayer player, Identifier layerId,
                                  Identifier oldOriginId, Identifier newOriginId) {
        this.player = player;
        this.layerId = layerId;
        this.oldOriginId = oldOriginId;
        this.newOriginId = newOriginId;
    }

    public ServerPlayer getPlayer() { return player; }
    public Identifier getLayerId() { return layerId; }
    /** Null when this is a first-time selection. */
    public Identifier getOldOriginId() { return oldOriginId; }
    /** Null when the origin is being cleared (reset). */
    public Identifier getNewOriginId() { return newOriginId; }
}
