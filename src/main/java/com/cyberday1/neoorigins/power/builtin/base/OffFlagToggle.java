package com.cyberday1.neoorigins.power.builtin.base;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * A power whose on/off state is a persisted off-flag in {@code PlayerOriginData},
 * stored under the power's id with a pre-2.2.24 fallback key.
 */
public interface OffFlagToggle<C extends PowerConfiguration> {

    boolean isToggledOff(ServerPlayer player, C config, Identifier id);

    /** False when the config makes the power always-on, with no toggle to switch. */
    boolean hasToggle(C config);

    String legacyToggleKey(C config);
}
