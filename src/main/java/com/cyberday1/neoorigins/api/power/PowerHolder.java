package com.cyberday1.neoorigins.api.power;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * A resolved power instance: the type paired with its decoded configuration.
 * Used internally to avoid repeated deserialization.
 */
public final class PowerHolder<C extends PowerConfiguration> {
    private final ResourceLocation id;
    private final PowerType<C> type;
    private final C config;
    private final Component name;
    private final Component description;

    public PowerHolder(ResourceLocation id, PowerType<C> type, C config, Component name, Component description) {
        this.id = id;
        this.type = type;
        this.config = config;
        this.name = name;
        this.description = description;
    }

    public ResourceLocation id()      { return id; }
    public PowerType<C> type()        { return type; }
    public C config()                 { return config; }
    public Component name()           { return name; }
    public Component description()    { return description; }

    /** Returns true if this power occupies a keybind slot (has active behaviour). */
    public boolean isActive()                              { return type.isActivePower(config); }

    // Power dispatch is single-threaded on the server main thread, but ThreadLocal
    // is safer than a static field if anything ever dispatches off-thread (and the
    // overhead is negligible). PowerType subclasses that need to know which power
    // they're being invoked as can read PowerHolder.currentDispatchId().
    private static final ThreadLocal<ResourceLocation> CURRENT_DISPATCH_ID = new ThreadLocal<>();

    public static ResourceLocation currentDispatchId() { return CURRENT_DISPATCH_ID.get(); }

    public void onGranted(ServerPlayer player)          { ResourceLocation prev = CURRENT_DISPATCH_ID.get(); CURRENT_DISPATCH_ID.set(id); try { type.onGranted(player, config); } finally { CURRENT_DISPATCH_ID.set(prev); } }
    public void onRevoked(ServerPlayer player)          { ResourceLocation prev = CURRENT_DISPATCH_ID.get(); CURRENT_DISPATCH_ID.set(id); try { type.onRevoked(player, config); } finally { CURRENT_DISPATCH_ID.set(prev); } }
    public void onTick(ServerPlayer player)             { ResourceLocation prev = CURRENT_DISPATCH_ID.get(); CURRENT_DISPATCH_ID.set(id); try { type.onTick(player, config);    } finally { CURRENT_DISPATCH_ID.set(prev); } }
    public void onLogin(ServerPlayer player)            { ResourceLocation prev = CURRENT_DISPATCH_ID.get(); CURRENT_DISPATCH_ID.set(id); try { type.onLogin(player, config);   } finally { CURRENT_DISPATCH_ID.set(prev); } }
    public void onActivated(ServerPlayer player)        { ResourceLocation prev = CURRENT_DISPATCH_ID.get(); CURRENT_DISPATCH_ID.set(id); try { type.onActivated(player, config); } finally { CURRENT_DISPATCH_ID.set(prev); } }
    public void onRespawn(ServerPlayer player)          { ResourceLocation prev = CURRENT_DISPATCH_ID.get(); CURRENT_DISPATCH_ID.set(id); try { type.onRespawn(player, config); } finally { CURRENT_DISPATCH_ID.set(prev); } }
    public void onHit(ServerPlayer player, float amount){ ResourceLocation prev = CURRENT_DISPATCH_ID.get(); CURRENT_DISPATCH_ID.set(id); try { type.onHit(player, config, amount); } finally { CURRENT_DISPATCH_ID.set(prev); } }
    public void onKill(ServerPlayer player, LivingEntity killed) { ResourceLocation prev = CURRENT_DISPATCH_ID.get(); CURRENT_DISPATCH_ID.set(id); try { type.onKill(player, config, killed); } finally { CURRENT_DISPATCH_ID.set(prev); } }
}
