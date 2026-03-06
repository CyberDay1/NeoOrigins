package com.cyberday1.neoorigins.api.power;

import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Abstract base class for all power types.
 * Each subclass is registered once in the PowerType registry and knows how to:
 *  - Decode its config C from JSON
 *  - Apply/remove its effect on a player
 */
public abstract class PowerType<C extends PowerConfiguration> {

    /** Codec for deserializing this power's configuration from JSON. */
    public abstract Codec<C> codec();

    /** Called when this power is granted to a player (or on login if they already have it). */
    public void onGranted(ServerPlayer player, C config) {}

    /** Called when this power is revoked from a player. */
    public void onRevoked(ServerPlayer player, C config) {}

    /** Called every server tick while the player has this power. Default: no-op. */
    public void onTick(ServerPlayer player, C config) {}

    /** Called when the player logs in with this power already active. */
    public void onLogin(ServerPlayer player, C config) {
        onGranted(player, config);
    }

    /** Called when the player presses a Skill key while this power occupies that slot. Default: no-op. */
    public void onActivated(ServerPlayer player, C config) {}

    /**
     * Called when the player respawns with this power active.
     * Default: re-runs onGranted() so attribute modifiers etc. are re-applied after death.
     */
    public void onRespawn(ServerPlayer player, C config) {
        onGranted(player, config);
    }

    /**
     * Called when the player takes damage while this power is active.
     * Default: no-op. Used by Route B powers like origins:self_action_when_hit.
     */
    public void onHit(ServerPlayer player, C config, float amount) {}

    /**
     * Called when the player kills a living entity while this power is active.
     * Default: no-op.
     */
    public void onKill(ServerPlayer player, C config, LivingEntity killed) {}

    /**
     * Returns true if this power type is keybind-slot-eligible (active).
     * Passive powers return false (default).
     * Override to return true in types that implement {@link #onActivated}.
     * After Phase 7: {@code AbstractActivePower} overrides this once; individual
     * active types will no longer need their own override.
     */
    public boolean isActivePower() { return false; }

    /**
     * Config-aware variant called by {@link PowerHolder#isActive()}.
     * Override in types where activeness depends on the specific config instance
     * (e.g. CompatPower, where only some configs have an onActivated consumer).
     * Default delegates to {@link #isActivePower()}.
     */
    public boolean isActivePower(C config) { return isActivePower(); }

    // Legacy — delegates to isActivePower(); existing overrides of isActive() still work.
    // After Phase 7: active types will override isActivePower() via AbstractActivePower;
    // these methods will be removed.
    public boolean isActive() { return isActivePower(); }
    public boolean isActive(C config) { return isActive(); }
}
