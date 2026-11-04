package com.cyberday1.neoorigins.api.event;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.power.registry.PowerTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class PowerGrantedEvent extends PlayerEvent {
    private final PowerHolder<?> power;

    public PowerGrantedEvent(ServerPlayer player, PowerHolder<?> power) {
        super(player);
        this.power = power;
    }

    public ResourceLocation getPowerId() { return power.id(); }

    /** The power that was granted: its type, config, name and condition. */
    public PowerHolder<?> getPower() { return power; }

    public PowerType<?> getPowerType() { return power.type(); }

    /** The power type's registry id, e.g. {@code neoorigins:natural_glide}. */
    public ResourceLocation getPowerTypeId() {
        return getEntity().server.registryAccess().registryOrThrow(PowerTypes.REGISTRY_KEY).getKey(power.type());
    }

    /** The decoded config. Cast it to your own config class after checking {@link #getPowerType()}. */
    public PowerConfiguration getConfig() { return power.config(); }

    @Override
    public ServerPlayer getEntity() {
        return (ServerPlayer) super.getEntity();
    }
}
