package com.cyberday1.neoorigins.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Public way to ask <em>any</em> entity renderer how big a shadow it draws.
 *
 * <p>Needed because a morphed player's shadow has to come from the morph
 * target's renderer, not the player's, and {@code getShadowRadius} is
 * {@code protected} — a class outside the renderer hierarchy cannot call it.
 *
 * <p>An invoker rather than an access transformer, for the same reason the
 * sound getters use one: {@code getShadowRadius} is overridden as
 * {@code protected} by {@code LivingEntityRenderer}, {@code MobRenderer} and
 * three more vanilla renderers, and widening only the base declaration while a
 * subclass still narrows it is a load-time visibility error. The invoker adds a
 * public bridge inside {@code EntityRenderer} that still dispatches virtually,
 * so the baby-size tweaks those overrides apply are honoured.
 */
@Mixin(EntityRenderer.class)
public interface EntityRendererShadowInvoker {

    @Invoker("getShadowRadius")
    float neoorigins$getShadowRadius(Entity entity);
}
