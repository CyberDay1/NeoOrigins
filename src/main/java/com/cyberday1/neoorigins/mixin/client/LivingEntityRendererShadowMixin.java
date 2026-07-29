package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.MorphRenderHandler;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
 * {@code PlayerRenderer.render} — but the shadow is drawn by
 * {@code EntityRenderDispatcher.render} <em>after</em> that call returns, so
 * cancelling the event cannot suppress it. The result was a player-shaped
 * shadow under a slime, a bat, or an ender dragon.
 *
 * <p>Only the radius is redirected, not the drawing: everything vanilla decides
 * around it — whether shadows are on at all, the dispatcher's own shadow flag,
 * invisibility, the fade with distance and the 32-block cap — is left to run
 * exactly as it does for any other entity. A radius of zero (an armour stand,
 * say) therefore means "this morph casts no shadow", which is the right answer
 * rather than a special case.
 *
 * <p>Mixed into {@code LivingEntityRenderer} rather than the player's own
 * renderer because that is where the override lives; non-player living entities
 * pay one {@code instanceof} for it.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererShadowMixin {

    @Inject(
        method = "getShadowRadius(Lnet/minecraft/world/entity/LivingEntity;)F",
        at = @At("HEAD"),
        cancellable = true)
    private void neoorigins$morphShadowRadius(LivingEntity entity, CallbackInfoReturnable<Float> cir) {
        if (!(entity instanceof Player player)) return;
        float radius = MorphRenderHandler.morphShadowRadius(player);
        if (radius >= 0.0f) cir.setReturnValue(radius);
    }
}
