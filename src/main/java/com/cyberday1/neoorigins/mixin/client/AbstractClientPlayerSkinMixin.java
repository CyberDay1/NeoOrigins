package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.MorphSkinResolver;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the {@code skin} half of the {@code neoorigins:entity_model} power:
 * a player-skin morph, where the player keeps their own model and only the
 * textures drawn on it change.
 *
 * <p>{@code getSkin()} is the single point every part of player rendering goes
 * through — the dispatcher chooses the slim-vs-wide renderer from the skin's
 * model, and the body, cape and elytra layers all read the same record — so
 * patching it here covers all of them at once. The alternative hook,
 * {@code RenderPlayerEvent.Pre}, fires after the arm width has already been
 * decided and would leave a slim morph with wide arms.
 *
 * <p>Injected at RETURN rather than HEAD on purpose: the player's real skin is
 * the base that the morph's textures are layered over, so a morph that only
 * sets a cape leaves the body alone. Client-side only.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerSkinMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void neoorigins$applyMorphSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        PlayerSkin morphed = MorphSkinResolver.apply(self.getId(), cir.getReturnValue());
        if (morphed != null) {
            cir.setReturnValue(morphed);
        }
    }
}
