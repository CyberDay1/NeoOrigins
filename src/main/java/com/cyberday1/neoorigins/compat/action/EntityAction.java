package com.cyberday1.neoorigins.compat.action;

import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface EntityAction {
    void execute(ServerPlayer player);

    static EntityAction noop() { return p -> {}; }
}
