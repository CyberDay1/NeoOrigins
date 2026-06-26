package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientInvisibilityArmorState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides worn armor for players made invisible by {@code neoorigins:invisibility}
 * with {@code render_armor: false}.
 *
 * <p>Vanilla renders the {@link HumanoidArmorLayer} regardless of invisibility —
 * that's the well-known "invisible body, floating armor" look. When the rendered
 * player carries the invisibility power's armor-hide flag (broadcast to all
 * tracking clients and mirrored in {@link ClientInvisibilityArmorState}), we
 * cancel the whole armor layer so the player is truly invisible.
 *
 * <p>The skip requires BOTH (a) the per-power render-armor-off flag AND (b) the
 * player actually being invisible right now ({@code isInvisible()}). Requiring
 * the flag means a player merely invisible from a vanilla potion or another mod
 * keeps their visible armor exactly as vanilla intends. Requiring the live
 * invisibility — which is synced to every client for free as a normal mob effect —
 * means the armor reappears the instant the power's {@code power_condition} gate
 * stops holding (the invisibility effect lapses), so we never show a fully-visible
 * player with missing armor. The flag itself therefore only needs to flip on
 * grant/revoke/config, not on every condition tick. Client-side only.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void neoorigins$hideArmorForTrueInvisibility(
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, LivingEntity livingEntity,
            float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (livingEntity instanceof Player player
                && player.isInvisible()
                && ClientInvisibilityArmorState.shouldHideArmor(player.getId())) {
            ci.cancel();
        }
    }
}
