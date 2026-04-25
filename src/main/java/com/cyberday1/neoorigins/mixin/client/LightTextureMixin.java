package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientActivePowers;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Client-side brightness boost for the {@code "enhanced_vision"} capability tag.
 *
 * <p>Intercepts every STORE to the local {@code f5} — vanilla's night-vision
 * blend factor inside {@code updateLightTexture} on 1.21.1 — and raises it to
 * at least {@link #EXPOSURE} when the local player has the capability. Vanilla's
 * three write sites (NIGHT_VISION scale, CONDUIT_POWER waterVision pass-through,
 * else 0.0F) all run through the modifier; the default-case 0.0F becomes the
 * floor, and the inner {@code if (f5 > 0.0F)} lerp inside the 16×16 pixel loop
 * pulls the lightmap toward normalized-max brightness.
 *
 * <p>Using {@code name="f5"} (LVT lookup) instead of an INVOKE owner target
 * because an earlier {@code @WrapOperation} on {@code Player.getWaterVision}
 * silently failed — javac emits {@code INVOKEVIRTUAL LocalPlayer.getWaterVision}
 * and mixin owner matching is exact.
 *
 * <p><b>Version-specific LVT note:</b> In 1.21.1, {@code f5} is the NV blend
 * and {@code f4} is the darkness-scale subtractor (line 144: {@code if (f4 > 0.0F)
 * vector3f1.add(-f4,-f4,-f4)}). Do NOT target {@code f4} here — doing so
 * effectively applies the DARKNESS mob effect at the EXPOSURE intensity.
 * In newer MC (26.x, GPU-UBO lightmap) the NV blend migrates to {@code f4} —
 * the port must re-verify the LVT name against that version's source.
 *
 * <p>Real NIGHT_VISION potion and real CONDUIT_POWER still win: we take
 * {@code max} with vanilla's value, so stacking a potion on top still works.
 *
 * <p>Client-side only. {@code require = 0} so the mixin silently no-ops if a
 * future MC version refactors the method shape.
 */
// priority = 1500 (default 1000) so we apply AFTER mods like Alex's Caves
// that also mixin into LightTexture. Higher priority = applied later =
// our @ModifyVariable on f5 wins over their lightmap stomp in their cave
// biomes. Tester reported enhanced_vision broken under Alex's Caves —
// this is the standard mitigation when two mods both write the lightmap.
@Mixin(value = LightTexture.class, priority = 1500)
public abstract class LightTextureMixin {

    /** Darkness-compensation factor; 0 = no boost, 1 = full night-vision-equivalent. */
    private static final float EXPOSURE = 1.0F;

    @ModifyVariable(
        method = "updateLightTexture(F)V",
        at = @At("STORE"),
        name = "f5",
        require = 0
    )
    private float neoorigins$boostNightVisionFloor(float original) {
        boolean has = ClientActivePowers.hasCapability("enhanced_vision");
        return has ? Math.max(original, EXPOSURE) : original;
    }
}
