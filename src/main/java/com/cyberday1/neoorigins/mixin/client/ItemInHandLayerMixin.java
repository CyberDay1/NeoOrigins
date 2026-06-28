package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientInvisibilityArmorState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides items held in hand for players made invisible by
 * {@code neoorigins:invisibility} with {@code render_armor: false} (true
 * invisibility). The armor-hide flag doubles as the held-item-hide flag — for a
 * truly invisible player, floating armor and a floating sword are the same
 * giveaway.
 *
 * <p>Vanilla renders the held-item layer regardless of invisibility, so a player
 * with {@code render_armor:false} would still trail a visible weapon/tool. This
 * cancels the whole {@link ItemInHandLayer} render under the SAME condition the
 * armor mixin uses: the rendered player carries the invisibility power's
 * armor-hide flag AND is currently invisible. {@code PlayerItemInHandLayer}
 * extends {@code ItemInHandLayer} and only overrides {@code renderArmWithItem}
 * (not {@code render}), so injecting here covers players too.
 *
 * <p>Requiring BOTH the flag and the live {@code isInvisible()} keeps a player
 * invisible from a vanilla potion / another source rendering their held item
 * exactly as vanilla intends, and makes the item reappear the instant the
 * power's {@code power_condition} gate stops holding (the invisibility effect
 * lapses). Client-side only.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void neoorigins$hideHeldItemForTrueInvisibility(
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
