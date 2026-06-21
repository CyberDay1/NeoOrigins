package com.cyberday1.neoorigins.client.renderer;

import com.cyberday1.neoorigins.api.content.vfx.BakedMeshModel;
import com.cyberday1.neoorigins.content.ThrownSwordProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Renders {@link ThrownSwordProjectile} as a spinning spectral blade in flight,
 * reusing the same {@code spectral_sword.bakedmesh} model + tint as the
 * {@link ProjectileRainRenderer} storm blades so the thrown sword and the rain
 * it seeds read as one effect. The blade's long axis (+Z) is aimed along its
 * velocity (tip leading) and spun about that travel axis as it flies.
 */
public class ThrownSwordRenderer extends EntityRenderer<ThrownSwordProjectile> {

    private static final String MODEL_PATH = "/assets/neoorigins/geo/spectral_sword.bakedmesh";
    /** Baked model is ~20 units long; scale to ~1.1 blocks for a flying blade. */
    private static final float MODEL_SCALE = 0.055f;
    private static BakedMeshModel model;

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "textures/entity/spectral_sword.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucent(TEXTURE);

    /** Spectral tint (cold steel-blue), matching the storm blades. */
    private static final int TINT_R = 175, TINT_G = 215, TINT_B = 255, TINT_A = 235;
    /** Degrees of spin per tick about the travel axis (1 rev ≈ 13 ticks). */
    private static final float SPIN_PER_TICK = 27f;

    public ThrownSwordRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(ThrownSwordProjectile entity) {
        return TEXTURE;
    }

    @Override
    public void render(ThrownSwordProjectile entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (model == null) {
            model = BakedMeshModel.load(MODEL_PATH, MODEL_SCALE);
        }

        // Aim the blade along its current travel direction (tip = +Z leads).
        Vec3 v = entity.getDeltaMovement();
        double horiz = Math.sqrt(v.x * v.x + v.z * v.z);
        float aimYaw, aimPitch;
        if (v.lengthSqr() > 1.0e-6) {
            aimYaw = (float) (Mth.atan2(v.x, v.z) * (180.0 / Math.PI));
            aimPitch = (float) (Mth.atan2(v.y, horiz) * (180.0 / Math.PI));
        } else {
            aimYaw = -entity.getYRot();
            aimPitch = -entity.getXRot();
        }
        float spin = (entity.tickCount + partialTick) * SPIN_PER_TICK;

        poseStack.pushPose();
        // Yaw about Y, then pitch about X, point +Z down the velocity vector.
        poseStack.mulPose(Axis.YP.rotationDegrees(aimYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-aimPitch));
        // Spin about the blade's long (travel) axis.
        poseStack.mulPose(Axis.ZP.rotationDegrees(spin));
        var consumer = buffer.getBuffer(RENDER_TYPE);
        model.renderTinted(poseStack, consumer, TINT_R, TINT_G, TINT_B, TINT_A,
            0xF000F0, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }
}
