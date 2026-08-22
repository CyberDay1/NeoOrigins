package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.BreathOutOfFluidPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets an active conduit reach aquatic origins standing on dry land.
 *
 * <p>Vanilla's {@code ConduitBlockEntity.applyEffects} only grants
 * {@code CONDUIT_POWER} to players for whom {@code isInWaterOrRain()} holds:
 *
 * <pre>{@code
 * if (pos.closerThan(player.blockPosition(), j) && player.isInWaterOrRain()) {
 *     player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 260, 0, true, true));
 * }
 * }</pre>
 *
 * <p>That makes the effect unreachable in exactly the case
 * {@link BreathOutOfFluidPower} cares about. Its handler treats Conduit Power as
 * a magical air supply, but a drying-out player is by definition out of water,
 * so the effect was never present to be found — the conduit exemption there, and
 * the matching {@code on_land} clause in {@code AttributeModifierPower}, were
 * both dead branches. The one case that appeared to work was a player leaving
 * water near a conduit: the 260-tick effect they carried out with them paused
 * the drain for ~13 seconds and then lapsed with nothing to renew it.
 *
 * <p>Wrapping the gate rather than re-implementing a conduit search keeps
 * vanilla's own activation state, radius ({@code effectBlocks.size()/7*16}) and
 * {@code closerThan} distance check authoritative — the inflated AABB above it
 * is only a broad phase. {@code applyEffects} is only reached
 * from {@code serverTick} every 40 ticks against a 260-tick effect duration, so
 * renewal has a wide margin and this adds no scan of its own.
 *
 * <p>Scoped to players carrying the power: everyone else keeps vanilla
 * behaviour and still has to be in water or rain.
 */
@Mixin(ConduitBlockEntity.class)
public abstract class ConduitBlockEntityMixin {

    @WrapOperation(
        method = "applyEffects",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;isInWaterOrRain()Z"
        )
    )
    private static boolean neoorigins$reachDryingOutPlayers(Player player, Operation<Boolean> original) {
        if (original.call(player)) return true;
        // applyEffects runs from serverTick only, so every player in the list is
        // a ServerPlayer; the pattern check is a guard, not a real branch.
        return player instanceof ServerPlayer sp
            && ActiveOriginService.hasCapability(sp, BreathOutOfFluidPower.DRIES_OUT_CAPABILITY);
    }
}
