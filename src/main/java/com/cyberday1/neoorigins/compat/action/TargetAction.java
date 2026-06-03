package com.cyberday1.neoorigins.compat.action;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * An entity-action that runs against a generic {@link LivingEntity} target,
 * with the actor (the power holder / caster, always a {@link ServerPlayer})
 * available for source attribution.
 *
 * <p>Companion to the player-typed {@link EntityAction}. It exists so the
 * bientity {@code target_action} verb can run effect-style verbs on a
 * non-player <em>mob</em> target — not just on player targets — for the subset
 * of verbs whose effect is entity-general (they touch only
 * {@code LivingEntity}/{@code Entity} methods). See {@link TargetActionParser}.
 */
@FunctionalInterface
public interface TargetAction {
    void execute(LivingEntity target, ServerPlayer actor);
}
