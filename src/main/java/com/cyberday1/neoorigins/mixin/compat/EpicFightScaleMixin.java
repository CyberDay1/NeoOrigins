package com.cyberday1.neoorigins.mixin.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the vanilla {@code generic.scale} attribute to Epic Fight's patched
 * renderer. Epic Fight bypasses vanilla's {@code LivingEntityRenderer.scale()}
 * call entirely, so origins with non-1.0 scale (Tiny, Dwarf, Golem, etc.)
 * snap to full size when Epic Fight takes over rendering in combat mode.
 *
 * <p>Injected at HEAD of {@code mulPoseStack} which is called right after
 * {@code PoseStack.pushPose()} in the render method, before Epic Fight
 * applies its bone transforms. {@code @Coerce} is used on the Epic Fight
 * parameters (Armature, LivingEntityPatch) so we don't need Epic Fight on
 * our compile classpath.</p>
 *
 * <p>{@code @Pseudo} makes this mixin optional — if Epic Fight isn't
 * installed, the target class doesn't exist and the mixin is silently
 * skipped.</p>
 */
@Pseudo
@Mixin(targets = "yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer")
public abstract class EpicFightScaleMixin {

    @Inject(
        method = "mulPoseStack(Lcom/mojang/blaze3d/vertex/PoseStack;Lyesman/epicfight/api/model/Armature;Lnet/minecraft/world/entity/LivingEntity;Lyesman/epicfight/world/capabilities/entitypatch/LivingEntityPatch;F)V",
        at = @At("HEAD"),
        require = 0,
        remap = false
    )
    private void neoorigins$applyEntityScale(
            PoseStack poseStack, @Coerce Object armature, LivingEntity entity,
            @Coerce Object entitypatch, float partialTicks,
            CallbackInfo ci) {
        float scale = entity.getScale();
        if (scale != 1.0F) {
            poseStack.scale(scale, scale, scale);
        }
    }
}
