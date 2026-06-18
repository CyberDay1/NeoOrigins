package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.MobsIgnorePlayerPower;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cancels {@link AvoidEntityGoal} when the entity being fled is a player with
 * an active {@code neoorigins:mobs_ignore_player} power that matches this mob's
 * type. Untamed cats, ocelots and foxes use this goal to bolt from players;
 * with the flag they should simply ignore the player rather than run.
 *
 * <p>The work only happens when the goal would otherwise fire (a flee target
 * was found and it's a server player), so the per-tick cost on the vast
 * majority of avoid-goal evaluations is a single boolean check.
 */
@Mixin(AvoidEntityGoal.class)
public abstract class AvoidEntityGoalMixin {

    @Shadow protected PathfinderMob mob;
    @Shadow protected LivingEntity toAvoid;

    @Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
    private void neoorigins$ignoreFlaggedPlayer(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        if (this.toAvoid instanceof ServerPlayer sp
                && MobsIgnorePlayerPower.suppressesAvoidance(sp, this.mob)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canContinueToUse", at = @At("RETURN"), cancellable = true)
    private void neoorigins$stopFleeingFlaggedPlayer(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        if (this.toAvoid instanceof ServerPlayer sp
                && MobsIgnorePlayerPower.suppressesAvoidance(sp, this.mob)) {
            cir.setReturnValue(false);
        }
    }
}
