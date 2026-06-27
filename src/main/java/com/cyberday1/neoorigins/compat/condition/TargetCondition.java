package com.cyberday1.neoorigins.compat.condition;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * An <em>entity-general</em> predicate over a {@link LivingEntity} target plus
 * the acting player. The condition counterpart to
 * {@link com.cyberday1.neoorigins.compat.action.TargetAction}: it tests only
 * {@code LivingEntity}/{@code Entity} state (type, tags, health, effects, …) and
 * never a player-only subsystem, so it can run against an arbitrary mob — not
 * just a {@link ServerPlayer}.
 *
 * <p>Used to filter the entities an {@code area_of_effect} (and therefore an
 * aura) touches: e.g. <em>players only</em> ({@code entity_type:
 * minecraft:player}), a specific mob, or an entity-type {@code #tag} group.
 */
@FunctionalInterface
public interface TargetCondition {
    boolean test(LivingEntity target, ServerPlayer actor);
}
