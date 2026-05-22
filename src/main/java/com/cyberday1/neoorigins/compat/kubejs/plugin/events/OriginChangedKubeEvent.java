package com.cyberday1.neoorigins.compat.kubejs.plugin.events;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.ResourceLocation;
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
    private final ResourceLocation layerId;
    private final ResourceLocation oldOriginId;
    private final ResourceLocation newOriginId;

    public OriginChangedKubeEvent(ServerPlayer player, ResourceLocation layerId,
                                  ResourceLocation oldOriginId, ResourceLocation newOriginId) {
        this.player = player;
        this.layerId = layerId;
        this.oldOriginId = oldOriginId;
        this.newOriginId = newOriginId;
    }

    public ServerPlayer getPlayer() { return player; }
    public ResourceLocation getLayerId() { return layerId; }
    /** Null when this is a first-time selection. */
    public ResourceLocation getOldOriginId() { return oldOriginId; }
    /** Null when the origin is being cleared (reset). */
    public ResourceLocation getNewOriginId() { return newOriginId; }
}
