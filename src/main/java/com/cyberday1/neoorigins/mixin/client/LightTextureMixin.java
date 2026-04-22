package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientActivePowers;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Client-side brightness boost for the {@code "enhanced_vision"} capability tag.
 *
 * <p>Vanilla's {@code updateLightTexture(float)} computes a local {@code f5} that
 * drives the "boost dark pixels toward max brightness" lerp — normally non-zero only
 * when the player has the NIGHT_VISION or (water + CONDUIT_POWER) effects. This mixin
 * raises that floor to {@link #EXPOSURE} when the local player has the
 * {@code enhanced_vision} capability active AND vanilla wouldn't already be applying
 * a stronger boost (e.g. potion night vision still wins).
 *
 * <p>The effect: darker end of the lightmap gets pulled up without the full-screen
 * tint, HUD icon, or end-of-duration flicker of the status effect.
 *
 * <p>Client-side only. If {@code LightTexture} is absent (pure server build), the
 * mixin simply never loads.
 *
 * <p>Exposure is hardcoded here rather than plumbed from per-power config; the
 * capability-tag sync channel carries only tags, and per-power exposure is not
 * currently worth the extra payload. Revisit if playtest demands per-origin variance.
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {

    /** Darkness-compensation factor; 0 = no boost, 1 = full night-vision-equivalent. */
    private static final float EXPOSURE = 0.7F;

    /**
     * {@code f5} in vanilla is the night-vision blend factor, used immediately after
     * this point as {@code if (f5 > 0.0F) vector3f1.lerp(vector3f5, f5)}. By intercepting
     * the first LOAD of this local we boost the floor without touching any of the
     * three assignment branches above — robust to MC refactors that reorder them.
     *
     * <p>{@code ordinal = 6} corresponds to {@code f5} in the local variable table of
     * {@code updateLightTexture(float)} (locals before it: {@code f, f1, f2, f3, f4, f6},
     * then {@code f5}). If a future MC version reorders the preceding locals the
     * injection will fall back to no-op thanks to {@code defaultRequire: 0} on the
     * client mixin section, and enhanced_vision will silently degrade to no-effect.
     */
    @ModifyVariable(
        method = "updateLightTexture(F)V",
        at = @At(value = "LOAD", ordinal = 0),
        ordinal = 6,
        require = 0
    )
    private float neoorigins$boostNightVisionFloor(float original) {
        if (!ClientActivePowers.hasCapability("enhanced_vision")) return original;
        // Preserve the larger of vanilla's value (e.g. real NIGHT_VISION potion at max)
        // and our exposure floor — origins:enhanced_vision adds, never subtracts.
        return Math.max(original, EXPOSURE);
    }
}
