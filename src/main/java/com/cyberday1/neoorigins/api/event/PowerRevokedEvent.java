package com.cyberday1.neoorigins.api.event;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.power.registry.PowerTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class PowerRevokedEvent extends PlayerEvent {
    private final PowerHolder<?> power;

    public PowerRevokedEvent(ServerPlayer player, PowerHolder<?> power) {
        super(player);
        this.power = power;
    }

    public Identifier getPowerId() { return power.id(); }

    /** The power that was revoked: its type, config, name and condition. */
    public PowerHolder<?> getPower() { return power; }

    public PowerType<?> getPowerType() { return power.type(); }

    /** The power type's registry id, e.g. {@code neoorigins:natural_glide}. */
    public Identifier getPowerTypeId() { return PowerTypes.getId(power.type()); }

    /** The decoded config. Cast it to your own config class after checking {@link #getPowerType()}. */
    public PowerConfiguration getConfig() { return power.config(); }

    @Override
    public ServerPlayer getEntity() {
        return (ServerPlayer) super.getEntity();
    }
}
