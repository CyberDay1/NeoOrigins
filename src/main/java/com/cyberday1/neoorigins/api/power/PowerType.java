package com.cyberday1.neoorigins.api.power;

import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

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

    /**
     * Whether this power should occupy a hotkey / skill slot in the active-power
     * list (and thus be triggerable by a key press). Defaults to
     * {@link #isActivePower(Object)}. A power can be active — reachable by the
     * {@code activate_power} action — yet decline a hotkey slot by overriding
     * this to return false (e.g. CompatPower configs flagged
     * {@code "disable_hotkey": true}), so they fire only programmatically.
     */
    public boolean occupiesHotkeySlot(C config) { return isActivePower(config); }

    /**
     * Capability tags this power grants while granted and (if toggleable) toggled on.
     * Client-observable — used by client-predicted mixins to decide whether to alter
     * vanilla behavior (e.g. {@code "wall_climb"} causes the player's {@code onClimbable()}
     * to return true while pressed against a wall).
     *
     * <p>Tags are short lowercase strings, conventionally matching a vanilla feature
     * or a cross-cutting mechanic. Default: no capabilities.
     */
    public Set<String> capabilities(C config) { return Set.of(); }

    /**
     * Player-aware capability variant called during active-power sync.
     * Override when capabilities depend on runtime state (conditions,
     * cooldowns, resource levels, etc.). Default delegates to
     * {@link #capabilities(PowerConfiguration)}.
     */
    public Set<String> capabilities(ServerPlayer player, C config) { return capabilities(config); }

    // ── Mob-origin support ──────────────────────────────────────────────────
    //
    // The lifecycle methods above are hard-typed to ServerPlayer and cannot be
    // used for a non-player mob. Mob support is therefore an explicit, opt-in
    // capability: a power that can meaningfully affect an arbitrary
    // LivingEntity overrides appliesToMobs() → true and implements
    // applyToMob/removeFromMob directly against the entity (attributes, mob
    // effects, etc.), instead of reusing onGranted/onRevoked.

    /** True if this power can be applied to a non-player {@link LivingEntity}
     *  via {@link #applyToMob}/{@link #removeFromMob}. Default: false. */
    public boolean appliesToMobs(C config) { return false; }

    /** Apply this power's effect directly to a mob. Only called when
     *  {@link #appliesToMobs} is true. Default: no-op. */
    public void applyToMob(LivingEntity mob, C config) {}

    /** Remove this power's effect from a mob. Only called when
     *  {@link #appliesToMobs} is true. Default: no-op. */
    public void removeFromMob(LivingEntity mob, C config) {}

}
