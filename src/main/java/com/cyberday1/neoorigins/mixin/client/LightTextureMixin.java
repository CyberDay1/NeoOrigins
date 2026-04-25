package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientActivePowers;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-side brightness boost for the {@code "enhanced_vision"} capability tag.
 *
 * <p>On 26.x, vanilla's lightmap is driven by {@link LightmapRenderStateExtractor}
 * populating a {@link LightmapRenderState} each tick — the GPU shader then consumes it.
 * The {@code nightVisionEffectIntensity} field drives the "boost dark pixels toward
 * max brightness" path in the shader; normally non-zero only when the player has the
 * NIGHT_VISION or (water + CONDUIT_POWER) effects. This mixin raises that floor to
 * {@link #EXPOSURE} when the local player has the {@code enhanced_vision} capability
 * active AND vanilla wouldn't already be applying a stronger boost (e.g. real night
 * vision potion at max still wins).
 *
 * <p>The effect: darker end of the lightmap gets pulled up without the full-screen
 * tint, HUD icon, or end-of-duration flicker of the status effect.
 *
 * <p>Client-side only. Class name is kept as {@code LightTextureMixin} for parity with
 * the 1.21.1 branch even though the class it targets is now called
 * {@code LightmapRenderStateExtractor} — the mental model (a brightness mixin) is the
 * same, and matching names across branches simplifies cross-version porting.
 *
 * <p>Exposure is hardcoded here rather than plumbed from per-power config; the
 * capability-tag sync channel carries only tags, and per-power exposure is not
 * currently worth the extra payload. Revisit if playtest demands per-origin variance.
 */
// priority = 1500 (default 1000) so we apply AFTER mods like Alex's Caves
// that also mixin into the lightmap pipeline. Higher priority = applied
// later = our @Inject(at = TAIL) write to nightVisionEffectIntensity wins
// over their stomp in their cave biomes. Tester reported enhanced_vision
// broken under Alex's Caves — this is the standard mitigation when two
// mods both write the lightmap.
@Mixin(value = LightmapRenderStateExtractor.class, priority = 1500)
public abstract class LightTextureMixin {

    /** Darkness-compensation factor; 0 = no boost, 1 = full night-vision-equivalent. */
    private static final float EXPOSURE = 0.7F;

    @Inject(
        method = "extract(Lnet/minecraft/client/renderer/state/LightmapRenderState;F)V",
        at = @At("TAIL"),
        require = 0
    )
    private void neoorigins$boostNightVisionFloor(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
        if (!renderState.needsUpdate) return;
        if (!ClientActivePowers.hasCapability("enhanced_vision")) return;
        // Preserve the larger of vanilla's value (e.g. real NIGHT_VISION potion at max)
        // and our exposure floor — origins:enhanced_vision adds, never subtracts.
        renderState.nightVisionEffectIntensity =
            Math.max(renderState.nightVisionEffectIntensity, EXPOSURE);
    }
}
