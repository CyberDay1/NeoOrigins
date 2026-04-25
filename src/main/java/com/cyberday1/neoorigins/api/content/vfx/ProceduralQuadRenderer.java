package com.cyberday1.neoorigins.api.content.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Base renderer for procedurally-animated 2D-quad VFX — the "two crossed
 * billboards with time-math rotation + pulse" pattern used by the magic-orb
 * projectile and similar effects. No model file required.
 *
 * <p>Renders in two layers:
 * <ul>
 *   <li><b>Core</b> — a near-white quad (slightly tinted by effect color)
 *       that spins faster. Gives the bright center of the orb.</li>
 *   <li><b>Glow</b> — a larger quad in the full effect color that spins
 *       slower (reverse direction) and pulses in scale. Gives the coloured
 *       halo.</li>
 * </ul>
 *
 * <p>Each layer is submitted as two quads crossed at 90° so the effect
 * looks volumetric from any angle — no billboarding math required.
 *
 * <p>Subclass to provide the texture + render-state-to-color mapping:
 * <pre>{@code
 * public class MyOrbRenderer extends ProceduralQuadRenderer<MyOrb, MyOrbRenderState> {
 *     private static final ResourceLocation TEXTURE =
 *         ResourceLocation.fromNamespaceAndPath("mymod", "textures/entity/orb.png");
 *
 *     public MyOrbRenderer(EntityRendererProvider.Context ctx) { super(ctx, TEXTURE); }
 *
 *     @Override
 *     public MyOrbRenderState createRenderState() { return new MyOrbRenderState(); }
 *
 *     @Override
 *     public void extractRenderState(MyOrb entity, MyOrbRenderState state, float partialTick) {
 *         super.extractRenderState(entity, state, partialTick);
 *         AbstractVfxRenderState.extract(entity, state);
 *     }
 * }
 * }</pre>
 *
 * <p>API status: stable. Added in 2.0.
 */
@OnlyIn(Dist.CLIENT)
public abstract class ProceduralQuadRenderer<T extends Entity, S extends AbstractVfxRenderState>
        extends EntityRenderer<T, S> {

    /** Core spin speed, degrees/tick (yaw). Subclass override for faster/slower. */
    protected float coreYawPerTick() { return 20.0f; }
    /** Core spin speed, degrees/tick (pitch). */
    protected float corePitchPerTick() { return 14.0f; }
    /** Core quad scale. */
    protected float coreScale() { return 0.3f; }

    /** Glow spin speed (yaw). Negative for counter-rotation vs. core. */
    protected float glowYawPerTick() { return -8.0f; }
    protected float glowPitchPerTick() { return -5.6f; }
    /** Base glow scale — pulse is layered on top of this. */
    protected float glowBaseScale() { return 0.7f; }
    /** Pulse amplitude — 0.08 = ±8% size oscillation. */
    protected float glowPulseAmplitude() { return 0.08f; }
    /** Pulse frequency in radians per tick. */
    protected float glowPulseFrequency() { return 0.15f; }
    /** Glow alpha 0-255. Lower = subtler halo. */
    protected int glowAlpha() { return 140; }

    /** Core tint ratio toward white (0 = full effect color, 1 = pure white). 0.7 = mostly white with slight tint. */
    protected float coreTintTowardWhite() { return 0.7f; }

    protected ProceduralQuadRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /**
     * Subclass provides the render type (texture + blending). Called during
     * the base class's render flow — subclasses should not override {@code submit}.
     */
    protected abstract RenderType renderType();

    /**
     * Resolve the RGB core color from the render state. Default reads
     * {@link AbstractVfxRenderState#effectType} through {@link VfxEffectTypes}.
     * Override to use a different source (per-entity custom color, etc.).
     */
    protected int[] resolveColor(S state) {
        return VfxEffectTypes.get(state.effectType);
    }

    /**
     * Base class handles the vanilla {@code submit(...)} flow — subclasses
     * do not need to override it. Calls {@link #submitQuads(AbstractVfxRenderState, PoseStack, SubmitNodeCollector, RenderType)}
     * with the subclass-provided {@link #renderType()}.
     */
    @Override
    public void submit(S state, PoseStack poseStack,
                       SubmitNodeCollector collector,
                       net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        submitQuads(state, poseStack, collector, renderType());
        super.submit(state, poseStack, collector, camera);
    }

    /**
     * Submit the two layers of quads via the collector. Internal helper —
     * the base class's {@code submit} calls this. Subclasses typically do
     * not need to invoke it directly.
     */
    protected void submitQuads(S state, PoseStack poseStack,
                               SubmitNodeCollector collector, RenderType renderType) {
        int[] color = resolveColor(state);
        float time = state.lifetime + state.partialTick;

        // Core — near-white, fast spin
        float blend = coreTintTowardWhite();
        final int cr = (int) (255 * blend + color[0] * (1 - blend));
        final int cg = (int) (255 * blend + color[1] * (1 - blend));
        final int cb = (int) (255 * blend + color[2] * (1 - blend));

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * coreYawPerTick()));
        poseStack.mulPose(Axis.XP.rotationDegrees(time * corePitchPerTick()));
        float cs = coreScale();
        poseStack.scale(cs, cs, cs);
        collector.submitCustomGeometry(poseStack, renderType,
            (pose, consumer) -> renderQuad(consumer, pose, cr, cg, cb, 255));
        poseStack.mulPose(Axis.YP.rotationDegrees(90f));
        collector.submitCustomGeometry(poseStack, renderType,
            (pose, consumer) -> renderQuad(consumer, pose, cr, cg, cb, 255));
        poseStack.popPose();

        // Glow — full effect color, slower reverse spin, pulsing
        float pulse = 1.0f + glowPulseAmplitude() * (float) Math.sin(time * glowPulseFrequency());
        float gs = glowBaseScale() * pulse;
        final int ga = glowAlpha();
        final int gr = color[0], gg = color[1], gb = color[2];

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * glowYawPerTick()));
        poseStack.mulPose(Axis.XP.rotationDegrees(time * glowPitchPerTick()));
        poseStack.scale(gs, gs, gs);
        collector.submitCustomGeometry(poseStack, renderType,
            (pose, consumer) -> renderQuad(consumer, pose, gr, gg, gb, ga));
        poseStack.mulPose(Axis.YP.rotationDegrees(90f));
        collector.submitCustomGeometry(poseStack, renderType,
            (pose, consumer) -> renderQuad(consumer, pose, gr, gg, gb, ga));
        poseStack.popPose();
    }

    /** Emit a single ±0.5 quad in the current pose plane. */
    protected static void renderQuad(VertexConsumer consumer, PoseStack.Pose pose,
                                     int r, int g, int b, int a) {
        consumer.addVertex(pose, -0.5f, -0.5f, 0f).setColor(r, g, b, a).setUv(0f, 1f)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0f, 1f, 0f);
        consumer.addVertex(pose,  0.5f, -0.5f, 0f).setColor(r, g, b, a).setUv(1f, 1f)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0f, 1f, 0f);
        consumer.addVertex(pose,  0.5f,  0.5f, 0f).setColor(r, g, b, a).setUv(1f, 0f)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0f, 1f, 0f);
        consumer.addVertex(pose, -0.5f,  0.5f, 0f).setColor(r, g, b, a).setUv(0f, 0f)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0f, 1f, 0f);
    }
}
