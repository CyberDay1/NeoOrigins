package com.cyberday1.neoorigins.api.event;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class PowerRevokedEvent extends PlayerEvent {
    private final Identifier powerId;

    public PowerRevokedEvent(ServerPlayer player, Identifier powerId) {
        super(player);
        this.powerId = powerId;
    }

    public Identifier getPowerId() { return powerId; }

    @Override
    public ServerPlayer getEntity() {
        return (ServerPlayer) super.getEntity();
    }
}
