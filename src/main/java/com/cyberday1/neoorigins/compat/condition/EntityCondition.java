package com.cyberday1.neoorigins.compat.condition;

import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface EntityCondition {
    boolean test(ServerPlayer player);

    static EntityCondition alwaysTrue()  { return p -> true;  }
    static EntityCondition alwaysFalse() { return p -> false; }
}
