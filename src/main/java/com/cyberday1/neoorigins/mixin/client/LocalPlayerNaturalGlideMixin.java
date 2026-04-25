package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientActivePowers;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets origins with the {@code natural_glide} capability start elytra-style
 * gliding without needing to equip an elytra — client side.
 *
 * <p>The server-side companion {@code PlayerStartFallFlyingMixin} bypasses the
 * elytra check inside {@code Player.tryToStartFallFlying} so the server accepts
 * the {@code START_FALL_FLYING} command. But the client is the gatekeeper that
 * <em>sends</em> that command: {@code LocalPlayer.aiStep} only fires the packet
 * when {@code this.tryToStartFallFlying()} returns true (26.1 dropped the
 * separate {@code canElytraFly} short-circuit; the elytra check is now folded
 * into {@code Player.canGlide}, which {@code tryToStartFallFlying} consults).
 * Without an elytra equipped, the call returns false and no packet is ever
 * sent — the server-side mixin never has anything to react to.
 *
 * <p>We redirect the {@code tryToStartFallFlying} call at the aiStep call site:
 * run vanilla first (still gated on {@code canGlide} inside); if vanilla
 * returns false but we have the {@code natural_glide} capability, replicate
 * vanilla's preconditions and call {@code startFallFlying()} directly so
 * the packet is then sent.
 *
 * <p>The recursive-looking call inside the redirect is fine — Mixin's
 * {@code @Redirect} only rewrites the call site we matched in {@code aiStep};
 * the call from this handler's body reaches vanilla's implementation
 * unchanged.
 *
 * <p>Client-side only — listed under the {@code client} array in
 * {@code neoorigins.mixins.json} so it never loads on a dedicated server.
 *
 * <p>{@code require = 0} so the mixin silently no-ops if a future MC version
 * refactors the aiStep call.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerNaturalGlideMixin {

    @Redirect(
        method = "aiStep",
        at = @At(value = "INVOKE",
            // Owner must match javac's bytecode resolution: `this.tryToStartFallFlying()`
            // inside LocalPlayer.aiStep compiles to INVOKEVIRTUAL LocalPlayer.tryToStartFallFlying,
            // not Player.tryToStartFallFlying — receiver static type wins. Targeting Player
            // here is a silent no-op even with require=0. (See feedback_mixin_owner_match.)
            target = "Lnet/minecraft/client/player/LocalPlayer;tryToStartFallFlying()Z"),
        require = 0
    )
    private boolean neoorigins$tryStartGlideWithCapability(LocalPlayer self) {
        // First-arg type must match the @Redirect target owner (LocalPlayer
        // here, not Player) — Mixin validates this and crashes on mismatch.
        // LocalPlayer extends Player, so all the inherited Player methods
        // we call below still resolve.
        if (self.tryToStartFallFlying()) return true;
        if (!ClientActivePowers.hasCapability("natural_glide")) return false;
        if (self.onGround() || self.isFallFlying() || self.isInWater()
            || self.hasEffect(MobEffects.LEVITATION)) {
            return false;
        }
        self.startFallFlying();
        return true;
    }
}
