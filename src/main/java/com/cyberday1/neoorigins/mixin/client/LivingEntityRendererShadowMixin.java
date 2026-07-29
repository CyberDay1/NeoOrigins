package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.MorphRenderHandler;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives a morphed player the morph's shadow instead of their own.
 *
 * <p>The shadow is the one part of a player's render that the morph could not
 * reach. {@code MorphRenderHandler} takes over by cancelling
 * {@code RenderPlayerEvent.Pre}, which fires from inside
 * {@code AvatarRenderer.submit} — but the shadow is submitted by
 * {@code EntityRenderDispatcher} <em>after</em> that call returns, from a radius
 * the render state was given during extraction. The result was a player-shaped
 * shadow under a slime, a bat, or an ender dragon.
 *
 * <p>Only the radius is redirected, not the drawing, and it is redirected here
 * rather than on the finished render state so that the blocks the shadow is cast
 * onto are gathered for the corrected radius — overwriting it afterwards would
 * leave a big shadow clipped to a small patch of ground. Everything vanilla
 * decides around it — whether shadows are on at all, invisibility, the fade with
 * distance and the 32-block cap — is left to run exactly as it does for any
 * other entity. A radius of zero (an armour stand, say) therefore means "this
 * morph casts no shadow", which is the right answer rather than a special case.
 *
 * <p>Mixed into {@code LivingEntityRenderer} rather than the player's own
 * renderer because that is where the override lives; non-player living entities
 * pay one {@code instanceof} for it.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererShadowMixin {

    @Inject(
        method = "getShadowRadius(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)F",
        at = @At("HEAD"),
        cancellable = true)
    private void neoorigins$morphShadowRadius(LivingEntityRenderState state,
                                              CallbackInfoReturnable<Float> cir) {
        if (!(state instanceof AvatarRenderState avatar)) return;
        float radius = MorphRenderHandler.morphShadowRadius(avatar);
        if (radius >= 0.0f) cir.setReturnValue(radius);
    }
}
