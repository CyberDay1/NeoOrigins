package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientGlowingEntities;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Outline colour for {@code neoorigins:entity_glow}. The entity renderer reads
 * {@code Entity.getTeamColor()} into the render state's outline colour; for an
 * entity one of the local player's glow powers selected with a colour of its own,
 * that colour is used instead. Wrapping only this call leaves the entity's team
 * colour alone everywhere else (nameplates, scoreboard).
 *
 * <p>1.21.1 wraps the same call in {@code LevelRenderer.renderLevel}; 26.x moved
 * it into {@code EntityRenderer.extractRenderState}.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererGlowColorMixin {

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I"))
    private int neoorigins$entityGlowColor(Entity entity, Operation<Integer> original) {
        Integer color = ClientGlowingEntities.colorFor(entity.getId());
        return color != null ? color : original.call(entity);
    }
}
