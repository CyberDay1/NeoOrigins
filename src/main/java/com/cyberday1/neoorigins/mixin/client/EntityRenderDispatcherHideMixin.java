package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientHiddenEntities;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Suppresses rendering of the entities the local player's
 * {@code neoorigins:prevent_entity_render} power hides from them.
 *
 * <p>{@code EntityRenderDispatcher#shouldRender} is the same seam Apoli hooks for
 * its own {@code prevent_entity_render}, and it is the right one: it is the
 * per-entity visibility test {@code LevelRenderer} consults before rendering, so
 * answering false drops the model, its layers, the nameplate, the fire overlay and
 * the shadow together, while leaving the entity itself intact client-side — sounds,
 * collision and targeting all behave as if it were visible, which is what "prevent
 * render" means in Apoli.
 *
 * <p>The verdict is not computed here. The server evaluates the power's
 * {@code entity_condition} and syncs the resulting id set (see
 * {@code PreventEntityRenderPower} for why), so this is a set lookup guarded by an
 * emptiness check — on every client without the power it costs one boolean read per
 * entity per frame.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherHideMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void neoorigins$hidePreventedEntities(
            E entity, Frustum frustum, double camX, double camY, double camZ,
            CallbackInfoReturnable<Boolean> cir) {
        if (ClientHiddenEntities.isEmpty()) return;
        if (ClientHiddenEntities.isHidden(entity.getId())) {
            cir.setReturnValue(false);
        }
    }
}
