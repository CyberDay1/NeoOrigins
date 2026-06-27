package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientInvisibilityArmorState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides worn armor for players made invisible by {@code neoorigins:invisibility}
 * with {@code render_armor: false}.
 *
 * <p>Vanilla submits the {@link HumanoidArmorLayer} regardless of invisibility —
 * that's the well-known "invisible body, floating armor" look. When the rendered
 * player carries the invisibility power's armor-hide flag we cancel the whole
 * armor layer so the player is truly invisible.
 *
 * <p><b>26.1 render-state adaptation.</b> On 1.21.1 this layer's {@code render}
 * method received the {@code LivingEntity} directly, so the mixin looked the flag
 * up by entity id and read {@code player.isInvisible()} live. On 26.1 the layer is
 * render-state-based: {@code submit(PoseStack, SubmitNodeCollector, int, S, float,
 * float)} receives a {@link HumanoidRenderState} and never sees the entity. The
 * armor-hide flag is therefore carried onto the render state via NeoForge render
 * data ({@link ClientInvisibilityArmorState#HIDE_ARMOR_KEY}), stamped by the
 * {@code PlayerRenderer} render-state modifier from the server-synced
 * {@link ClientInvisibilityArmorState} set.
 *
 * <p>The skip requires BOTH (a) the per-power render-armor-off flag on the state
 * AND (b) the state actually being invisible right now
 * ({@link net.minecraft.client.renderer.entity.state.EntityRenderState#isInvisible}).
 * Requiring the flag means a player merely invisible from a vanilla potion or
 * another mod keeps their visible armor exactly as vanilla intends. Requiring the
 * live invisibility — synced to every client for free as a normal mob effect —
 * means the armor reappears the instant the power's {@code power_condition} gate
 * stops holding (the invisibility effect lapses), so we never show a fully-visible
 * player with missing armor. The flag itself therefore only needs to flip on
 * grant/revoke/config, not on every condition tick. Client-side only.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void neoorigins$hideArmorForTrueInvisibility(
            PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
            HumanoidRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (state.isInvisible
                && Boolean.TRUE.equals(state.getRenderData(ClientInvisibilityArmorState.HIDE_ARMOR_KEY))) {
            ci.cancel();
        }
    }
}
