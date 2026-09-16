package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.util.SneakLedgeGuard;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Issue #134: a sneaking player with one of our step-height powers walks off a
 * one-block ledge instead of stopping at it.
 *
 * <p>{@code maybeBackOffFromEdge} reads {@code maxUpStep()} exactly once, into a
 * local it then hands to both {@code isAboveGround} and every
 * {@code canFallAtLeast} probe. Rewriting that single read is therefore the whole
 * fix and keeps the two helpers agreeing on one value — which is the invariant the
 * Forge comment above {@code isAboveGround} exists to protect. Nothing in
 * {@code isAboveGround} itself is touched.
 *
 * <p>Applies on both logical sides because {@code Player} covers
 * {@code LocalPlayer} and {@code ServerPlayer}, and the inputs are all synced
 * attribute state, so neither side can decide differently and start correcting
 * the other.
 */
// hub: neoorigins/sneak-ledge-guard.md
@Mixin(Player.class)
public abstract class PlayerSneakLedgeMixin {

    @ModifyExpressionValue(
        method = "maybeBackOffFromEdge",
        at = @At(value = "INVOKE", target = "maxUpStep()F"))
    private float neoorigins$probeLedgeAtVanillaDepth(float original) {
        return SneakLedgeGuard.edgeProbeDepth((Player) (Object) this, original);
    }
}
