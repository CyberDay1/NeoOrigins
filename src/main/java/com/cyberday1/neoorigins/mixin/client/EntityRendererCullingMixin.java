package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.event.MorphHitboxEvents;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops a morph being culled off-screen when its collision box is too small to
 * stand in for its silhouette.
 *
 * <p>Normally there is nothing to do: {@code MorphHitboxEvents} gives a morphed
 * player the morph target's real box, and the off-screen test works off that
 * box, so the two agree. This is for the cases where it declines — a morph that
 * opted out of hitboxes, a target whose size couldn't be measured, and a morph
 * held back because the player is walled in — where a large model would wink out
 * while part of it should still be on screen. {@code MorphHitboxEvents} decides
 * who qualifies; this only reports the verdict.
 *
 * <p>1.21.1 does the same job by setting {@code Entity.noCulling}. 26.x replaced
 * that flag with this renderer method, so what is one field assignment there
 * needs a mixin here.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererCullingMixin {

    @Inject(
        method = "affectedByCulling(Lnet/minecraft/world/entity/Entity;)Z",
        at = @At("HEAD"),
        cancellable = true)
    private void neoorigins$morphCulling(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player player && MorphHitboxEvents.isCullingExempt(player)) {
            cir.setReturnValue(false);
        }
    }
}
