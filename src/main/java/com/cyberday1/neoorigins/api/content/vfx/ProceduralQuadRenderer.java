package com.cyberday1.neoorigins.api.content.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;

/**
 * Base renderer for procedurally-animated 2D-quad VFX — the "two crossed
 * billboards with time-math rotation + pulse" pattern used by the magic-orb
 * projectile and similar effects.
 *
 * <p><b>1.21.1 variant.</b> The 26.1 variant extends
 * {@code EntityRenderer<T, S>} (state-pattern) and uses
 * {@code submit(state, poseStack, collector, camera)}; this 1.21.1
 * variant extends {@code EntityRenderer<T>} (single type arg) and uses
 * {@code render(entity, yaw, partialTick, poseStack, buffer, light)}.
 * Both expose the same abstract hooks to subclasses:
 * <ul>
 *   <li>{@link #coreYawPerTick()}, {@link #corePitchPerTick()}, {@link #coreScale()} — core animation params</li>
 *   <li>{@link #glowYawPerTick()}, {@link #glowPitchPerTick()}, {@link #glowBaseScale()},
 *       {@link #glowPulseAmplitude()}, {@link #glowPulseFrequency()}, {@link #glowAlpha()} — glow animation params</li>
 *   <li>{@link #coreTintTowardWhite()} — core colour blend toward white</li>
 *   <li>{@link #resolveColor(AbstractVfxRenderState)} — entity → RGB lookup</li>
 *   <li>{@link #renderType()} — subclass-provided render type (texture + blending)</li>
 *   <li>{@link #extractRenderState(Entity, AbstractVfxRenderState, float)} — subclass populates the state POJO per-frame</li>
 * </ul>
 *
 * <p>Subclass code is version-portable: the hook method signatures are
 * identical on 26.1 and 1.21.1. Only this base class's internals differ.
 *
 * <p>API status: stable. Added in 2.0.
 */
public abstract class ProceduralQuadRenderer<T extends Entity, S extends AbstractVfxRenderState>
        extends EntityRenderer<T> {

    protected ProceduralQuadRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    // ─── Per-version abstract hooks ──────────────────────────────────────

    /** Subclass provides a fresh state POJO (reused per render). */
    protected abstract S createRenderState();

    /** Subclass populates state from entity + partialTick each frame. */
    protected abstract void extractRenderState(T entity, S state, float partialTick);

    /** Subclass provides the render type (texture + blending). */
    protected abstract RenderType renderType();

    // ─── Animation parameters (identical on both versions) ───────────────

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

    /** Core tint ratio toward white (0 = full effect color, 1 = pure white). */
    protected float coreTintTowardWhite() { return 0.7f; }

    /**
     * Resolve the RGB core color from the render state. Default reads
     * {@link AbstractVfxRenderState#effectType} through {@link VfxEffectTypes}.
     * Override to use a different source (per-entity custom color, etc.).
     */
    protected int[] resolveColor(S state) {
        return VfxEffectTypes.get(state.effectType);
    }

    // ─── 1.21.1 render flow ──────────────────────────────────────────────

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        S state = createRenderState();
        state.partialTick = partialTick;
        extractRenderState(entity, state, partialTick);

        // Resolve colours: explicit per-entity state.coreColor wins, else the
        // effect_type lookup via resolveColor(). Glow defaults to the core colour
        // when not given its own.
        int[] color = state.coreColor != null ? state.coreColor : resolveColor(state);
        int[] glow = state.glowColor != null ? state.glowColor : color;
        float time = state.lifetime + partialTick;

        VertexConsumer consumer = buffer.getBuffer(renderType());

        // Core — near-white, fast spin. Size from state (explicit/effect default)
        // or the hardcoded coreScale() fallback.
        float blend = coreTintTowardWhite();
        int cr = (int) (255 * blend + color[0] * (1 - blend));
        int cg = (int) (255 * blend + color[1] * (1 - blend));
        int cb = (int) (255 * blend + color[2] * (1 - blend));

        String shape = (state.shape == null || state.shape.isEmpty()) ? "cross" : state.shape;
        float cs = state.size > 0 ? state.size : coreScale();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * coreYawPerTick()));
        poseStack.mulPose(Axis.XP.rotationDegrees(time * corePitchPerTick()));
        poseStack.scale(cs, cs, cs);
        drawShape(shape, consumer, poseStack, cr, cg, cb, 255);
        poseStack.popPose();

        // Glow — full glow colour, slower reverse spin, pulsing. Size/alpha from
        // state or hardcoded fallbacks.
        float pulse = 1.0f + glowPulseAmplitude() * (float) Math.sin(time * glowPulseFrequency());
        float gs = (state.glowSize > 0 ? state.glowSize : glowBaseScale()) * pulse;
        int ga = state.glowAlpha >= 0 ? state.glowAlpha : glowAlpha();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * glowYawPerTick()));
        poseStack.mulPose(Axis.XP.rotationDegrees(time * glowPitchPerTick()));
        poseStack.scale(gs, gs, gs);
        drawShape(shape, consumer, poseStack, glow[0], glow[1], glow[2], ga);
        poseStack.popPose();

        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }

    // ─── Procedural-quad shapes ──────────────────────────────────────────
    //
    // All four shapes are built from the same ±0.5 quad primitive so they share
    // the renderType (texture + emissive translucency) and cost a handful of
    // quads each. The pose passed in already carries the spin + scale; each shape
    // just stamps quads in different planes. `cross` is the original two-billboard
    // look (back-compat default).

    /** Dispatch to the named shape. Unknown keys fall back to {@code cross}. */
    protected static void drawShape(String shape, VertexConsumer consumer, PoseStack poseStack,
                                    int r, int g, int b, int a) {
        switch (shape) {
            case "cube"   -> drawCube(consumer, poseStack, r, g, b, a);
            case "ring"   -> drawRing(consumer, poseStack, r, g, b, a);
            case "sphere" -> drawSphere(consumer, poseStack, r, g, b, a);
            case "cross"  -> drawCross(consumer, poseStack, r, g, b, a);
            default       -> drawCross(consumer, poseStack, r, g, b, a);
        }
    }

    /** Two crossed billboards (original 2.0 look). */
    protected static void drawCross(VertexConsumer consumer, PoseStack poseStack,
                                    int r, int g, int b, int a) {
        renderQuad(consumer, poseStack.last(), r, g, b, a);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(90f));
        renderQuad(consumer, poseStack.last(), r, g, b, a);
        poseStack.popPose();
    }

    /** Six axis-aligned faces of a unit cube (±0.5 box). */
    protected static void drawCube(VertexConsumer consumer, PoseStack poseStack,
                                   int r, int g, int b, int a) {
        // +Z / -Z
        renderQuad(consumer, poseStack.last(), r, g, b, a);
        for (float deg : new float[]{90f, 180f, 270f}) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(deg));
            renderQuad(consumer, poseStack.last(), r, g, b, a);
            poseStack.popPose();
        }
        // top / bottom
        for (float deg : new float[]{90f, -90f}) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.XP.rotationDegrees(deg));
            renderQuad(consumer, poseStack.last(), r, g, b, a);
            poseStack.popPose();
        }
    }

    /** Flat ring of {@value #RING_SEGMENTS} quads arranged in a circle (XY plane). */
    private static final int RING_SEGMENTS = 8;
    protected static void drawRing(VertexConsumer consumer, PoseStack poseStack,
                                   int r, int g, int b, int a) {
        for (int i = 0; i < RING_SEGMENTS; i++) {
            float deg = 360f * i / RING_SEGMENTS;
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(deg));
            poseStack.translate(0.55f, 0f, 0f);
            poseStack.scale(0.4f, 0.4f, 0.4f);
            renderQuad(consumer, poseStack.last(), r, g, b, a);
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
    protected static void drawSphere(VertexConsumer consumer, PoseStack poseStack,
                                     int r, int g, int b, int a) {
        // Crossed verticals
        drawCross(consumer, poseStack, r, g, b, a);
        // Horizontal great circle
        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(90f));
        renderQuad(consumer, poseStack.last(), r, g, b, a);
        poseStack.popPose();
        // Two 45° diagonal billboards to fill the silhouette
        for (float deg : new float[]{45f, -45f}) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(deg));
            renderQuad(consumer, poseStack.last(), r, g, b, a);
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
