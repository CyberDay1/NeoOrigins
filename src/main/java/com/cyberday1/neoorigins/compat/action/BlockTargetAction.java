package com.cyberday1.neoorigins.compat.action;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * A block-targeted action that runs against a single {@link BlockPos} in a
 * {@link ServerLevel}, with the actor (the power holder / caster, always a
 * {@link ServerPlayer}) available for source attribution.
 *
 * <p>Sibling of the entity-side SAMs {@link EntityAction}/{@link TargetAction}/
 * {@link BiEntityAction}. Where {@link TargetAction} is "the entity on the other
 * side of the interaction", this is "the <em>block</em> on the other side of the
 * interaction" — the block a projectile or raycast impacted. The impacted block
 * is published to {@link com.cyberday1.neoorigins.service.ActionContextHolder} as
 * a {@link com.cyberday1.neoorigins.service.EventPowerIndex.BlockHitContext} and
 * resolved via {@link ActionParser#extractBlockTarget(Object)}; the
 * {@code block_target_action} wrapper (and the context-resolving
 * {@link EntityAction} registrations in {@link BuiltinActions}) read it from
 * there rather than re-deriving it. See {@link BlockTargetActionParser}.
 */
@FunctionalInterface
public interface BlockTargetAction {
    void execute(ServerLevel level, BlockPos pos, ServerPlayer actor);
}
