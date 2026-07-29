package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientActivePowers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client-side brightness boost for the {@code "enhanced_vision"} capability tag.
 *
 * <p>Steers vanilla's night-vision blend inside {@code updateLightTexture} by
 * wrapping the two calls that compute it: the {@code LocalPlayer.hasEffect}
 * branch check (forced true for NIGHT_VISION when the capability is active, so
 * vanilla takes the night-vision path instead of the 0.0F default) and the
 * {@code GameRenderer.getNightVisionScale} value (floored at {@link #EXPOSURE};
 * when the player has no real night-vision effect we return the floor directly
 * because vanilla's scale function would NPE on the missing effect instance).
 *
 * <p><b>Why INVOKE wraps and not {@code @ModifyVariable}:</b> the previous
 * implementation modified the local {@code f5} by LVT name with
 * {@code require = 0}. Production NeoForge clients strip the
 * LocalVariableTable entirely (verified against the 1.21.1 installed client:
 * no LVT in {@code updateLightTexture}), so the name lookup never matched and
 * the mixin silently no-opped in every release build — enhanced_vision only
 * ever worked in dev (Discord report: Archer Hawk Eye "night vision" never
 * triggers). Slot indices are no safer: javac assigned the blend to slot 9 in
 * the NeoForm-recompiled dev jar but slot 8 in Mojang's original compile.
 * Method/owner INVOKE targets survive both environments (runtime uses official
 * mappings), so these wraps apply identically in dev and prod — and they hard
 * fail at apply time instead of silently doing nothing if a future version
 * refactors the method.
 *
 * <p>Owner note: javac emits {@code INVOKEVIRTUAL LocalPlayer.hasEffect} (the
 * static type of {@code this.minecraft.player}), and mixin owner matching is
 * exact — target {@code LocalPlayer}, not {@code LivingEntity}/{@code Player}.
 *
 * <p>Real NIGHT_VISION potion still wins where stronger: we take {@code max}
 * with vanilla's scale. CONDUIT_POWER users are unaffected unless the
 * capability is active, in which case the (full-strength) night-vision branch
 * supersedes the waterVision blend anyway.
 */
// priority = 1500 (default 1000) so we apply AFTER mods like Alex's Caves
// that also mixin into LightTexture. Higher priority = applied later =
// our wraps win over their lightmap stomp in their cave biomes. Tester
// reported enhanced_vision broken under Alex's Caves — this is the standard
// mitigation when two mods both write the lightmap.
@Mixin(value = LightTexture.class, priority = 1500)
public abstract class LightTextureMixin {

    /** Darkness-compensation factor; 0 = no boost, 1 = full night-vision-equivalent. */
    private static final float EXPOSURE = 1.0F;

    /**
     * The capability is granted AND the player hasn't switched night vision off
     * with the dedicated keybind. The status-effect flavour of night vision is
     * gated server-side (the effect simply stops being applied), but this
     * brightness boost is computed entirely client-side from a capability tag, so
     * the toggle has to be consulted here for the key to mean the same thing on
     * both paths. Defaults to enabled, so nothing changes for players who never
     * press it.
     */
    @org.spongepowered.asm.mixin.Unique
    private static boolean neoorigins$enhancedVisionActive() {
        return ClientActivePowers.hasCapability("enhanced_vision")
            && com.cyberday1.neoorigins.client.ClientNightVisionState.isEnabled();
    }

    @WrapOperation(
        method = "updateLightTexture(F)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"
        )
    )
    private boolean neoorigins$forceNightVisionBranch(LocalPlayer player, Holder<MobEffect> effect,
                                                      Operation<Boolean> original) {
        boolean has = original.call(player, effect);
        // Both hasEffect call sites in updateLightTexture route through here;
        // the holder check keeps the CONDUIT_POWER site untouched.
        if (!has && effect == MobEffects.NIGHT_VISION
                && neoorigins$enhancedVisionActive()) {
            return true;
        }
        return has;
    }

    @WrapOperation(
        method = "updateLightTexture(F)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;getNightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F"
        )
    )
    private float neoorigins$boostNightVisionScale(LivingEntity entity, float partialTick,
                                                   Operation<Float> original) {
        if (!neoorigins$enhancedVisionActive()) {
            return original.call(entity, partialTick);
        }
        if (!entity.hasEffect(MobEffects.NIGHT_VISION)) {
            // We forced the branch above without a real effect instance —
            // vanilla's scale function would NPE on getEffect(NIGHT_VISION).
            return EXPOSURE;
        }
        return Math.max(original.call(entity, partialTick), EXPOSURE);
    }
}
