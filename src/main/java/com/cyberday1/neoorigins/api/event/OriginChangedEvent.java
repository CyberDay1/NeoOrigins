package com.cyberday1.neoorigins.api.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import org.jetbrains.annotations.Nullable;

/**
 * Fired on the NeoForge event bus when a player's origin changes.
 * Cancellable — cancelling prevents the origin from being set.
 */
public class OriginChangedEvent extends PlayerEvent implements ICancellableEvent {
    private final ResourceLocation layer;
    @Nullable private final ResourceLocation oldOrigin;
    private ResourceLocation newOrigin;

    public OriginChangedEvent(ServerPlayer player, ResourceLocation layer,
                               @Nullable ResourceLocation oldOrigin, ResourceLocation newOrigin) {
        super(player);
        this.layer = layer;
        this.oldOrigin = oldOrigin;
        this.newOrigin = newOrigin;
    }

    public ResourceLocation getLayer() { return layer; }
    @Nullable public ResourceLocation getOldOrigin() { return oldOrigin; }
    public ResourceLocation getNewOrigin() { return newOrigin; }
    public void setNewOrigin(ResourceLocation newOrigin) { this.newOrigin = newOrigin; }

    @Override
    public ServerPlayer getEntity() {
        return (ServerPlayer) super.getEntity();
    }
}
