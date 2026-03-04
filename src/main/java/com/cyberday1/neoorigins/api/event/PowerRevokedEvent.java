package com.cyberday1.neoorigins.api.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class PowerRevokedEvent extends PlayerEvent {
    private final ResourceLocation powerId;

    public PowerRevokedEvent(ServerPlayer player, ResourceLocation powerId) {
        super(player);
        this.powerId = powerId;
    }

    public ResourceLocation getPowerId() { return powerId; }

    @Override
    public ServerPlayer getEntity() {
        return (ServerPlayer) super.getEntity();
    }
}
