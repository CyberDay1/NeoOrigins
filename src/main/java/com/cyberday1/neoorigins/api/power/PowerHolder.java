package com.cyberday1.neoorigins.api.power;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * A resolved power instance: the type paired with its decoded configuration.
 * Used internally to avoid repeated deserialization.
 */
public final class PowerHolder<C extends PowerConfiguration> {
    private final PowerType<C> type;
    private final C config;
    private final Component name;
    private final Component description;

    public PowerHolder(PowerType<C> type, C config, Component name, Component description) {
        this.type = type;
        this.config = config;
        this.name = name;
        this.description = description;
    }

    public PowerType<C> type()        { return type; }
    public C config()                 { return config; }
    public Component name()           { return name; }
    public Component description()    { return description; }

    /** Returns true if this power occupies a keybind slot (has active behaviour). */
    public boolean isActive()                              { return type.isActivePower(config); }

    public void onGranted(ServerPlayer player)          { type.onGranted(player, config); }
    public void onRevoked(ServerPlayer player)          { type.onRevoked(player, config); }
    public void onTick(ServerPlayer player)             { type.onTick(player, config); }
    public void onLogin(ServerPlayer player)            { type.onLogin(player, config); }
    public void onActivated(ServerPlayer player)        { type.onActivated(player, config); }
    public void onRespawn(ServerPlayer player)          { type.onRespawn(player, config); }
    public void onHit(ServerPlayer player, float amount){ type.onHit(player, config, amount); }
    public void onKill(ServerPlayer player, LivingEntity killed) { type.onKill(player, config, killed); }
}
