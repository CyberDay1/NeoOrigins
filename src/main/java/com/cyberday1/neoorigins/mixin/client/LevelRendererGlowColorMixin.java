package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientGlowingEntities;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Outline colour for {@code neoorigins:entity_glow}. The level renderer reads
 * {@code Entity.getTeamColor()} for each outlined entity; for an entity one of the
 * local player's glow powers selected with a colour of its own, that colour is
 * used instead. Wrapping only this call leaves the entity's team colour alone
 * everywhere else (nameplates, scoreboard).
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererGlowColorMixin {

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I"))
    private int neoorigins$entityGlowColor(Entity entity, Operation<Integer> original) {
        Integer color = ClientGlowingEntities.colorFor(entity.getId());
        return color != null ? color : original.call(entity);
    }
}
