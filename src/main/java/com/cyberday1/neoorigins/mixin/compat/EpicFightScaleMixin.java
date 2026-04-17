package com.cyberday1.neoorigins.mixin.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the vanilla {@code generic.scale} attribute to Epic Fight's patched
 * renderer. Epic Fight bypasses vanilla's {@code LivingEntityRenderer.scale()}
 * call entirely, so origins with non-1.0 scale (Tiny, Dwarf, Golem, etc.)
 * snap to full size when Epic Fight takes over rendering in combat mode.
 *
 * <p>Injected right after {@code PoseStack.pushPose()} in the render method,
 * before Epic Fight applies its own bone transforms. {@code @Pseudo} makes
 * this mixin optional — if Epic Fight isn't installed, the target class
 * doesn't exist and the mixin is silently skipped.</p>
 */
@Pseudo
@Mixin(targets = "yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer")
public abstract class EpicFightScaleMixin {

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
            shift = At.Shift.AFTER
        ),
        require = 0,
        remap = false
    )
    private void neoorigins$applyEntityScale(
            LivingEntity entity, Object entitypatch, LivingEntityRenderer<?, ?> renderer,
            MultiBufferSource buffer, PoseStack poseStack, int packedLight, float partialTicks,
            CallbackInfo ci) {
        float scale = entity.getScale();
        if (scale != 1.0F) {
            poseStack.scale(scale, scale, scale);
        }
    }
}
