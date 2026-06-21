package com.cyberday1.neoorigins.client.renderer;

import com.cyberday1.neoorigins.api.content.vfx.BakedMeshModel;
import com.cyberday1.neoorigins.content.ProjectileRainVfxEntity;
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
 * 1.21.1 renderer for {@link ProjectileRainVfxEntity}. Draws the whole ring of
 * spectral blades by rendering a single {@link BakedMeshModel} once per blade,
 * each transformed to its ring slot and animated through a descend → stab →
 * hold → fade cycle whose timing is read straight off the entity's own
 * {@code landingTick(i)} schedule, so the visual thunk lands on the exact tick
 * the server applies that blade's damage. See 26.1/26.2 twins.
 *
 * <p>The blade is the offline-baked "Heroes Blade" model (CC BY 4.0, see
 * CREDITS.md) loaded from the {@code NBM1} blob. Its long axis is +Z and it is
 * recentered to the origin, so the renderer points the tip downward with a
 * {@code +90°} rotation about X (vs the old voxel model's {@code 180°}).
 */
public class ProjectileRainRenderer extends EntityRenderer<ProjectileRainVfxEntity> {

    /** Baked model is in source-model units (~20 long); scale to ~1.6 blocks. */
    private static final float MODEL_SCALE = 0.08f;

    /** Datapack-selectable blade models, keyed by the entity's {@code model} id.
     *  spawn_projectile_rain (and its spawn_sword_rain alias) pass an id through
     *  the entity's synced {@code DATA_MODEL}; unknown ids fall back to "sword".
     *  Loaded lazily on first render so a bad path can't break class init. */
    private static final java.util.Map<String, String> MODEL_PATHS = java.util.Map.of(
        "sword", "/assets/neoorigins/geo/spectral_sword.bakedmesh");
    private static final java.util.Map<String, BakedMeshModel> MODEL_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static BakedMeshModel modelFor(String id) {
        String key = MODEL_PATHS.containsKey(id) ? id : "sword";
        return MODEL_CACHE.computeIfAbsent(key,
            k -> BakedMeshModel.load(MODEL_PATHS.get(k), MODEL_SCALE));
    }

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "textures/entity/spectral_sword.png");
    // Diffuse-shaded translucent (NOT emissive): emissive renders the blade as a
    // flat fullbright silhouette, which erased the model's 3D form and made it
    // read like the old voxel ghost-sword. entityTranslucent applies normal-based
    // diffuse shading so the fuller, bevel and crossguard are actually visible.
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucent(TEXTURE);

    /** Spectral tint (cold steel-blue) applied to every blade. */
    private static final int TINT_R = 175, TINT_G = 215, TINT_B = 255;
    /** Blocks above the ground the blade starts its fall from (high in the sky). */
    private static final float FALL_HEIGHT = 18.0f;
    /** Ticks a blade spends falling before it stabs. */
    private static final float FALL_TICKS = 11.0f;
    /** Ticks at the end of the entity lifetime over which blades fade out. */
    private static final float FADE_TICKS = 14.0f;
    /** Pivot lift so the rotated blade's tip lands near ground level
     *  (≈ half the baked blade length post-scale). */
    private static final float PIVOT_LIFT = 0.82f;
    private static final float BLADE_SCALE = 1.0f;

    public ProjectileRainRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(ProjectileRainVfxEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(ProjectileRainVfxEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Real-projectile mode: actual entities fall under physics and draw
        // themselves, so the choreographed fake blades would double up. Skip them.
        if (!entity.shouldRenderBlades()) {
            super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
            return;
        }

        float time = entity.getLifetime() + partialTick;
        int count = entity.getSwordCount();
        float maxLife = entity.getMaxLifetime();
        BakedMeshModel model = modelFor(entity.getModel());
        var consumer = buffer.getBuffer(RENDER_TYPE);

        for (int i = 0; i < count; i++) {
            float landing = entity.landingTick(i);
            float startFall = landing - FALL_TICKS;
            if (time < startFall) continue; // not yet falling

            // Height above ground: full at start of fall, 0 at the stab.
            float fallProgress = Mth.clamp((time - startFall) / FALL_TICKS, 0f, 1f);
            // Ease-in so the blade accelerates as it drops.
            float eased = fallProgress * fallProgress;

            // Fade in while falling, hold, then fade out at the end of life.
            float alpha = 0.35f + 0.55f * fallProgress;
            float fadeOutStart = maxLife - FADE_TICKS;
            if (time > fadeOutStart) {
                alpha *= Mth.clamp((maxLife - time) / FADE_TICKS, 0f, 1f);
            }
            if (alpha <= 0.02f) continue;
            int a = (int) (alpha * 255f);

            Vec3 off = entity.bladeLocalOffset(i);

            // Per-blade descent: each blade falls from high in the sky almost
            // straight down, with only a slight random tilt + azimuth so the
            // storm reads as blades raining vertically from above (not slanted
            // meteors). Landing points are already scattered across the disk.
            double hAz   = frac(Math.sin(i * 91.13 + 4.7) * 24634.21);
            double hTilt = frac(Math.sin(i * 53.71 + 1.3) * 19733.59);
            float az    = (float) (hAz * Math.PI * 2.0);
            float tilt  = (float) Math.toRadians(3.0 + 12.0 * hTilt); // 3–15° off vertical
            double sinT = Math.sin(tilt), cosT = Math.cos(tilt);
            // Unit vector from the ground impact point toward the blade's launch
            // point in the sky. The blade slides down this line as it falls.
            double skyX = sinT * Math.cos(az);
            double skyY = cosT;
            double skyZ = sinT * Math.sin(az);

            // remain: 1 at the start of the fall, 0 at the stab.
            float remain = 1f - eased;
            // Terrain follow: lower/raise this blade's whole descent column so it
            // stabs the per-point surface Y instead of the entity's flat center Y.
            // bladeGroundY uses the MOTION_BLOCKING heightmap (present client-side),
            // so this matches the server impact exactly. 0 when follow_terrain off.
            double groundDelta = entity.bladeGroundY(i) - entity.getY();
            double curX = off.x + skyX * FALL_HEIGHT * remain;
            double curY = groundDelta + skyY * FALL_HEIGHT * remain + PIVOT_LIFT;
            double curZ = off.z + skyZ * FALL_HEIGHT * remain;

            // Orientation: the tip leads along the travel direction (= -sky).
            float bladeYaw = (float) (Math.atan2(-skyX, -skyZ) * (180.0 / Math.PI));
            // 90° = straight down (tilt 0); leans toward horizontal as tilt grows.
            float bladePitch = 90f - (float) Math.toDegrees(tilt);
            // Tiny post-stab quiver.
            float quiver = fallProgress >= 1f
                ? Mth.sin((time - landing) * 0.9f) * 1.5f * Math.max(0f, 1f - (time - landing) / 6f)
                : 0f;

            poseStack.pushPose();
            poseStack.translate(curX, curY, curZ);
            poseStack.mulPose(Axis.YP.rotationDegrees(bladeYaw));
            // Baked blade's long axis is +Z; the X rotation points the tip along
            // the slanted descent (90° → straight down when tilt is 0).
            poseStack.mulPose(Axis.XP.rotationDegrees(bladePitch + quiver));
            poseStack.scale(BLADE_SCALE, BLADE_SCALE, BLADE_SCALE);
            // Fullbright lightmap keeps the spectral blade luminous; the
            // diffuse render type still shades its 3D form from the normals.
            model.renderTinted(poseStack, consumer, TINT_R, TINT_G, TINT_B, a, 0xF000F0, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }

        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }

    /** Fractional part — deterministic per-blade hash source for the meteor angles. */
    private static double frac(double v) { return v - Math.floor(v); }
}
