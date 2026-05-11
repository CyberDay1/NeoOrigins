package com.cyberday1.neoorigins.compat.action;

import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface EntityAction {
    void execute(ServerPlayer player);

    /** Singleton no-op — reference equality with {@code == NOOP} is intentional. */
    EntityAction NOOP = p -> {};

    static EntityAction noop() { return NOOP; }
}
