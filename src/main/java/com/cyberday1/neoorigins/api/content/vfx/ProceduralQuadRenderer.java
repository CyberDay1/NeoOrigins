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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

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
@OnlyIn(Dist.CLIENT)
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

        int[] color = resolveColor(state);
        float time = state.lifetime + partialTick;

        VertexConsumer consumer = buffer.getBuffer(renderType());

        // Core — near-white, fast spin
        float blend = coreTintTowardWhite();
        int cr = (int) (255 * blend + color[0] * (1 - blend));
        int cg = (int) (255 * blend + color[1] * (1 - blend));
        int cb = (int) (255 * blend + color[2] * (1 - blend));

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * coreYawPerTick()));
        poseStack.mulPose(Axis.XP.rotationDegrees(time * corePitchPerTick()));
        float cs = coreScale();
        poseStack.scale(cs, cs, cs);
        renderQuad(consumer, poseStack.last(), cr, cg, cb, 255);
        poseStack.mulPose(Axis.YP.rotationDegrees(90f));
        renderQuad(consumer, poseStack.last(), cr, cg, cb, 255);
        poseStack.popPose();

        // Glow — full effect color, slower reverse spin, pulsing
        float pulse = 1.0f + glowPulseAmplitude() * (float) Math.sin(time * glowPulseFrequency());
        float gs = glowBaseScale() * pulse;
        int ga = glowAlpha();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * glowYawPerTick()));
        poseStack.mulPose(Axis.XP.rotationDegrees(time * glowPitchPerTick()));
        poseStack.scale(gs, gs, gs);
        renderQuad(consumer, poseStack.last(), color[0], color[1], color[2], ga);
        poseStack.mulPose(Axis.YP.rotationDegrees(90f));
        renderQuad(consumer, poseStack.last(), color[0], color[1], color[2], ga);
        poseStack.popPose();

        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
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
