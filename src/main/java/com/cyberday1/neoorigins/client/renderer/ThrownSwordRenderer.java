package com.cyberday1.neoorigins.client.renderer;

import com.cyberday1.neoorigins.api.content.vfx.BakedMeshModel;
import com.cyberday1.neoorigins.content.ThrownSwordProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Renders {@link ThrownSwordProjectile} as a spinning spectral blade in flight,
 * reusing the same {@code spectral_sword.bakedmesh} model + tint as the
 * {@link ProjectileRainRenderer} storm blades so the thrown sword and the rain
 * it seeds read as one effect. The blade's long axis (+Z) is aimed along its
 * velocity (tip leading) and spun about that travel axis as it flies.
 *
 * <p>26.2 submit-pipeline variant — the aim velocity, fallback rotations and
 * age are snapshotted into {@link ThrownSwordRenderState}.
 */
public class ThrownSwordRenderer
        extends EntityRenderer<ThrownSwordProjectile, ThrownSwordRenderState> {

    private static final String MODEL_PATH = "/assets/neoorigins/geo/spectral_sword.bakedmesh";
    /** Baked model is ~20 units long; scale to ~1.1 blocks for a flying blade. */
    private static final float MODEL_SCALE = 0.055f;
    private static BakedMeshModel model;

    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath("neoorigins", "textures/entity/spectral_sword.png");
    private static final RenderType RENDER_TYPE = RenderTypes.entityTranslucent(TEXTURE);

    /** Spectral tint (cold steel-blue), matching the storm blades. */
    private static final int TINT_R = 175, TINT_G = 215, TINT_B = 255, TINT_A = 235;
    /** Degrees of spin per tick about the travel axis (1 rev ≈ 13 ticks). */
    private static final float SPIN_PER_TICK = 27f;

    public ThrownSwordRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ThrownSwordRenderState createRenderState() {
        return new ThrownSwordRenderState();
    }

    @Override
    public void extractRenderState(ThrownSwordProjectile entity, ThrownSwordRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        var v = entity.getDeltaMovement();
        state.velX = v.x;
        state.velY = v.y;
        state.velZ = v.z;
        state.fallbackYaw = entity.getYRot();
        state.fallbackPitch = entity.getXRot();
        state.age = entity.tickCount;
    }

    @Override
    public void submit(ThrownSwordRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        if (model == null) {
            model = BakedMeshModel.load(MODEL_PATH, MODEL_SCALE);
        }

        // Aim the blade along its current travel direction (tip = +Z leads).
        double vx = state.velX, vy = state.velY, vz = state.velZ;
        double horiz = Math.sqrt(vx * vx + vz * vz);
        float aimYaw, aimPitch;
        if (vx * vx + vy * vy + vz * vz > 1.0e-6) {
            aimYaw = (float) (Mth.atan2(vx, vz) * (180.0 / Math.PI));
            aimPitch = (float) (Mth.atan2(vy, horiz) * (180.0 / Math.PI));
        } else {
            aimYaw = -state.fallbackYaw;
            aimPitch = -state.fallbackPitch;
        }
        float spin = (state.age + state.partialTick) * SPIN_PER_TICK;

        poseStack.pushPose();
        // Yaw about Y, then pitch about X, point +Z down the velocity vector.
        poseStack.mulPose(Axis.YP.rotationDegrees(aimYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-aimPitch));
        // Spin about the blade's long (travel) axis.
        poseStack.mulPose(Axis.ZP.rotationDegrees(spin));
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, consumer) ->
            model.renderTinted(pose, consumer, TINT_R, TINT_G, TINT_B, TINT_A,
                0xF000F0, OverlayTexture.NO_OVERLAY));
        poseStack.popPose();

        super.submit(state, poseStack, collector, camera);
    }
}
