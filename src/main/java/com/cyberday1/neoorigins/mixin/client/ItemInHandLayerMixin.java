package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientInvisibilityArmorState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
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
 * <p><b>26.1 render-state adaptation.</b> Like the armor layer, the item-in-hand
 * layer is render-state-based on 26.1: {@code submit(PoseStack, SubmitNodeCollector,
 * int, S, float, float)} receives an {@link ArmedEntityRenderState} and never sees
 * the entity. The armor-hide flag is read off the render state via the same
 * NeoForge render-data key ({@link ClientInvisibilityArmorState#HIDE_ARMOR_KEY})
 * the armor mixin uses — stamped onto the player render state by the
 * {@code PlayerRenderer} render-state modifier. ({@code AvatarRenderState} /
 * {@code HumanoidRenderState} are {@code ArmedEntityRenderState} subtypes, so the
 * key set by the modifier is visible here.)
 *
 * <p>Requiring BOTH the flag and the live {@code isInvisible} keeps a player
 * invisible from a vanilla potion / another source rendering their held item
 * exactly as vanilla intends, and makes the item reappear the instant the power's
 * {@code power_condition} gate stops holding (the invisibility effect lapses).
 * Client-side only.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void neoorigins$hideHeldItemForTrueInvisibility(
            PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
            ArmedEntityRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (state.isInvisible
                && Boolean.TRUE.equals(state.getRenderData(ClientInvisibilityArmorState.HIDE_ARMOR_KEY))) {
            ci.cancel();
        }
    }
}
