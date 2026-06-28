package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientInvisibilityArmorState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
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
 * <p>Vanilla submits the held-item layer regardless of invisibility, so a player
 * with {@code render_armor:false} would still trail a visible weapon/tool. This
 * cancels the whole {@link ItemInHandLayer} submit under the SAME condition the
 * armor mixin uses. {@code PlayerItemInHandLayer} extends {@code ItemInHandLayer}
 * and only overrides {@code submitArmWithItem} (not {@code submit}), so injecting
 * here covers players too.
 *
 * <p><b>26.2 port note.</b> Like the armor layer, this hook is the render-state
 * {@code submit(PoseStack, SubmitNodeCollector, int, S, float, float)} taking an
 * {@link ArmedEntityRenderState} (not the live entity). We key off
 * {@link AvatarRenderState} — the player render state, a subtype of
 * {@code ArmedEntityRenderState} that carries both the entity {@code id} and the
 * inherited {@code isInvisible} flag — matching {@code HumanoidArmorLayerMixin}.
 * Only players produce an {@code AvatarRenderState}, so non-player humanoids are
 * left untouched.
 *
 * <p>Requiring BOTH the flag and the live {@code isInvisible} keeps a player
 * invisible from a vanilla potion / another source rendering their held item
 * exactly as vanilla intends, and makes the item reappear the instant the power's
 * {@code power_condition} gate stops holding (the invisibility effect lapses).
 * Client-side only.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true, require = 0)
    private void neoorigins$hideHeldItemForTrueInvisibility(
            PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
            ArmedEntityRenderState state, float limbSwing, float limbSwingAmount, CallbackInfo ci) {
        if (state instanceof AvatarRenderState avatar
                && avatar.isInvisible
                && ClientInvisibilityArmorState.shouldHideArmor(avatar.id)) {
            ci.cancel();
        }
    }
}
