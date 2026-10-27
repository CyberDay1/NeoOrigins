package com.cyberday1.neoorigins.power.builtin.base;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * A power whose on/off state is a persisted off-flag in {@code PlayerOriginData},
 * stored under the power's id with a pre-2.2.24 fallback key.
 */
public interface OffFlagToggle<C extends PowerConfiguration> {

    boolean isToggledOff(ServerPlayer player, C config, ResourceLocation id);

    /** False when the config makes the power always-on, with no toggle to switch. */
    boolean hasToggle(C config);

    String legacyToggleKey(C config);

    /**
     * True when this type reads the shared toggle-upkeep fields into
     * {@link com.cyberday1.neoorigins.power.util.PowerUpkeep}'s table at load.
     * {@code effect_over_time} carries its own cost fields and answers false.
     */
    default boolean readsSharedUpkeep() { return true; }

    /** True when this power charges an upkeep while it is on. */
    default boolean hasUpkeep(C config, ResourceLocation id) {
        return !com.cyberday1.neoorigins.power.util.PowerUpkeep.forPower(id).isFree();
    }
}
