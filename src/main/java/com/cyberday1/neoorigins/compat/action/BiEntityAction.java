package com.cyberday1.neoorigins.compat.action;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Apoli {@code bientity_action} — runs against a (actor, target) pair.
 * Mirrors Apoli's BiEntityAction architecture as a distinct functional
 * type from {@link EntityAction} because the signatures aren't
 * substitutable: an actor-only action can't be called with a target
 * argument, and vice-versa.
 *
 * <p>The actor is always a {@link ServerPlayer} (the holder of the power
 * that triggered the action). The target is a {@link LivingEntity} —
 * usually another mob or player. Sub-actions that require a player target
 * (e.g. resource changes, advancement grants) short-circuit when target
 * is not a {@code ServerPlayer}.
 */
@FunctionalInterface
public interface BiEntityAction {
    void execute(ServerPlayer actor, LivingEntity target);

    /** Singleton no-op — reference equality with {@code == NOOP} is intentional. */
    BiEntityAction NOOP = (a, t) -> {};

    static BiEntityAction noop() { return NOOP; }
}
