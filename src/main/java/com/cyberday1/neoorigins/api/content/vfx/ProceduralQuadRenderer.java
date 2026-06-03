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
        // Resolve colours: explicit per-entity state.coreColor wins, else the
        // effect_type lookup via resolveColor(). Glow defaults to the core colour
        // when not given its own.
        int[] color = state.coreColor != null ? state.coreColor : resolveColor(state);
        int[] glow = state.glowColor != null ? state.glowColor : color;
        float time = state.lifetime + state.partialTick;

        // Core — near-white, fast spin. Size from state (explicit/effect default)
        // or the hardcoded coreScale() fallback.
        float blend = coreTintTowardWhite();
        final int cr = (int) (255 * blend + color[0] * (1 - blend));
        final int cg = (int) (255 * blend + color[1] * (1 - blend));
        final int cb = (int) (255 * blend + color[2] * (1 - blend));

        String shape = (state.shape == null || state.shape.isEmpty()) ? "cross" : state.shape;
        float cs = state.size > 0 ? state.size : coreScale();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * coreYawPerTick()));
        poseStack.mulPose(Axis.XP.rotationDegrees(time * corePitchPerTick()));
        poseStack.scale(cs, cs, cs);
        drawShape(shape, collector, renderType, poseStack, cr, cg, cb, 255);
        poseStack.popPose();

        // Glow — full glow colour, slower reverse spin, pulsing. Size/alpha from
        // state or hardcoded fallbacks.
        float pulse = 1.0f + glowPulseAmplitude() * (float) Math.sin(time * glowPulseFrequency());
        float gs = (state.glowSize > 0 ? state.glowSize : glowBaseScale()) * pulse;
        final int ga = state.glowAlpha >= 0 ? state.glowAlpha : glowAlpha();
        final int gr = glow[0], gg = glow[1], gb = glow[2];

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * glowYawPerTick()));
        poseStack.mulPose(Axis.XP.rotationDegrees(time * glowPitchPerTick()));
        poseStack.scale(gs, gs, gs);
        drawShape(shape, collector, renderType, poseStack, gr, gg, gb, ga);
        poseStack.popPose();
    }

    // ─── Procedural-quad shapes ──────────────────────────────────────────
    //
    // All four shapes are built from the same ±0.5 quad primitive so they share
    // the renderType (texture + emissive translucency) and cost a handful of
    // quads each. The pose passed in already carries the spin + scale; each shape
    // just stamps quads in different planes. `cross` is the original two-billboard
    // look (back-compat default).
    //
    // 26.1 path: each quad is handed to the collector via submitCustomGeometry —
    // the per-quad pose snapshot is captured inside the lambda from the live
    // poseStack, so the push/mulPose/translate sequence mutates poseStack between
    // submits exactly as the 1.21.1 render() path mutated it between renderQuad
    // calls.

    /** Dispatch to the named shape. Unknown keys fall back to {@code cross}. */
    protected static void drawShape(String shape, SubmitNodeCollector collector, RenderType renderType,
                                    PoseStack poseStack, int r, int g, int b, int a) {
        switch (shape) {
            case "cube"   -> drawCube(collector, renderType, poseStack, r, g, b, a);
            case "ring"   -> drawRing(collector, renderType, poseStack, r, g, b, a);
            case "sphere" -> drawSphere(collector, renderType, poseStack, r, g, b, a);
            case "cross"  -> drawCross(collector, renderType, poseStack, r, g, b, a);
            default       -> drawCross(collector, renderType, poseStack, r, g, b, a);
        }
    }

    /** Submit one ±0.5 quad from the current poseStack state via the collector. */
    private static void submitQuad(SubmitNodeCollector collector, RenderType renderType,
                                   PoseStack poseStack, int r, int g, int b, int a) {
        final int fr = r, fg = g, fb = b, fa = a;
        collector.submitCustomGeometry(poseStack, renderType,
            (pose, consumer) -> renderQuad(consumer, pose, fr, fg, fb, fa));
    }

    /** Two crossed billboards (original 2.0 look). */
    protected static void drawCross(SubmitNodeCollector collector, RenderType renderType,
                                    PoseStack poseStack, int r, int g, int b, int a) {
        submitQuad(collector, renderType, poseStack, r, g, b, a);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(90f));
        submitQuad(collector, renderType, poseStack, r, g, b, a);
        poseStack.popPose();
    }

    /** Six axis-aligned faces of a unit cube (±0.5 box). */
    protected static void drawCube(SubmitNodeCollector collector, RenderType renderType,
                                   PoseStack poseStack, int r, int g, int b, int a) {
        // +Z / -Z
        submitQuad(collector, renderType, poseStack, r, g, b, a);
        for (float deg : new float[]{90f, 180f, 270f}) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(deg));
            submitQuad(collector, renderType, poseStack, r, g, b, a);
            poseStack.popPose();
        }
        // top / bottom
        for (float deg : new float[]{90f, -90f}) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.XP.rotationDegrees(deg));
            submitQuad(collector, renderType, poseStack, r, g, b, a);
            poseStack.popPose();
        }
    }

    /** Flat ring of {@value #RING_SEGMENTS} quads arranged in a circle (XY plane). */
    private static final int RING_SEGMENTS = 8;
    protected static void drawRing(SubmitNodeCollector collector, RenderType renderType,
                                   PoseStack poseStack, int r, int g, int b, int a) {
        for (int i = 0; i < RING_SEGMENTS; i++) {
            float deg = 360f * i / RING_SEGMENTS;
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(deg));
            poseStack.translate(0.55f, 0f, 0f);
            poseStack.scale(0.4f, 0.4f, 0.4f);
            submitQuad(collector, renderType, poseStack, r, g, b, a);
            poseStack.popPose();
        }
    }

    /**
     * Cheap faithful sphere: three orthogonal great-circle billboards plus the
     * crossed pair, giving a round volumetric read from any angle without a mesh.
     * (Simplified vs. a true tessellated sphere — the plan accepts the cheap
     * faithful version; with the emissive translucent material this reads as a
     * soft orb.)
     */
    protected static void drawSphere(SubmitNodeCollector collector, RenderType renderType,
                                     PoseStack poseStack, int r, int g, int b, int a) {
        // Crossed verticals
        drawCross(collector, renderType, poseStack, r, g, b, a);
        // Horizontal great circle
        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(90f));
        submitQuad(collector, renderType, poseStack, r, g, b, a);
        poseStack.popPose();
        // Two 45° diagonal billboards to fill the silhouette
        for (float deg : new float[]{45f, -45f}) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(deg));
            submitQuad(collector, renderType, poseStack, r, g, b, a);
            poseStack.popPose();
        }
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
