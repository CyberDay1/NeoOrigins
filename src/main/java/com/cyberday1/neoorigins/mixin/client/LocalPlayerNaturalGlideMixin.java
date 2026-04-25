package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientActivePowers;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets origins with the {@code natural_glide} capability start elytra-style
 * gliding without needing to equip an elytra — client side.
 *
 * <p>The server-side companion {@code PlayerStartFallFlyingMixin} bypasses the
 * elytra check inside {@code Player.tryToStartFallFlying} so the server accepts
 * the {@code START_FALL_FLYING} command. But on 1.21.1 the client is the
 * gatekeeper that <em>sends</em> that command:
 * {@code LocalPlayer.aiStep} only fires the packet when
 * {@code itemstack.canElytraFly(this) && this.tryToStartFallFlying()} is true.
 * Without an elytra equipped, both calls return false and no packet is ever
 * sent — the server-side mixin never has anything to react to.
 *
 * <p>We redirect both calls at that single aiStep call site:
 * <ul>
 *   <li>{@link #neoorigins$canElytraFlyOrCapability} — substitutes vanilla's
 *       elytra check with one that also accepts the {@code natural_glide}
 *       capability.</li>
 *   <li>{@link #neoorigins$tryStartGlideWithCapability} — runs vanilla
 *       {@code tryToStartFallFlying} first (still gated on elytra inside);
 *       if that fails but we have the capability, replicate vanilla's
 *       preconditions and call {@code startFallFlying()} directly.</li>
 * </ul>
 *
 * <p>The recursive-looking calls inside our redirect handlers are fine — Mixin's
 * {@code @Redirect} only rewrites the call site we matched in {@code aiStep};
 * calls from other methods (including these handlers' own bodies) reach the
 * vanilla implementations unchanged.
 *
 * <p>Client-side only — listed under the {@code client} array in
 * {@code neoorigins.mixins.json} so it never loads on a dedicated server.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerNaturalGlideMixin {

    @Redirect(
        method = "aiStep",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;canElytraFly(Lnet/minecraft/world/entity/LivingEntity;)Z"),
        require = 0
    )
    private boolean neoorigins$canElytraFlyOrCapability(ItemStack stack, LivingEntity entity) {
        if (stack.canElytraFly(entity)) return true;
        return ClientActivePowers.hasCapability("natural_glide");
    }

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
