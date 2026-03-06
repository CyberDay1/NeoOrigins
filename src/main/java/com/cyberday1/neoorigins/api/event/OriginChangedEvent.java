package com.cyberday1.neoorigins.api.event;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import org.jetbrains.annotations.Nullable;

/**
 * Fired on the NeoForge event bus when a player's origin changes.
 * Cancellable — cancelling prevents the origin from being set.
 */
public class OriginChangedEvent extends PlayerEvent implements ICancellableEvent {
    private final Identifier layer;
    @Nullable private final Identifier oldOrigin;
    private Identifier newOrigin;

    public OriginChangedEvent(ServerPlayer player, Identifier layer,
                               @Nullable Identifier oldOrigin, Identifier newOrigin) {
        super(player);
        this.layer = layer;
        this.oldOrigin = oldOrigin;
        this.newOrigin = newOrigin;
    }

    public Identifier getLayer() { return layer; }
    @Nullable public Identifier getOldOrigin() { return oldOrigin; }
    public Identifier getNewOrigin() { return newOrigin; }
    public void setNewOrigin(Identifier newOrigin) { this.newOrigin = newOrigin; }

    @Override
    public ServerPlayer getEntity() {
        return (ServerPlayer) super.getEntity();
    }
}
