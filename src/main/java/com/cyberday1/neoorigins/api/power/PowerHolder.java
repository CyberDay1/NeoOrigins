package com.cyberday1.neoorigins.api.power;

import net.minecraft.server.level.ServerPlayer;

/**
 * A resolved power instance: the type paired with its decoded configuration.
 * Used internally to avoid repeated deserialization.
 */
public final class PowerHolder<C extends PowerConfiguration> {
    private final PowerType<C> type;
    private final C config;

    public PowerHolder(PowerType<C> type, C config) {
        this.type = type;
        this.config = config;
    }

    public PowerType<C> type() { return type; }
    public C config() { return config; }

    public void onGranted(ServerPlayer player) { type.onGranted(player, config); }
    public void onRevoked(ServerPlayer player) { type.onRevoked(player, config); }
    public void onTick(ServerPlayer player) { type.onTick(player, config); }
    public void onLogin(ServerPlayer player) { type.onLogin(player, config); }
}
