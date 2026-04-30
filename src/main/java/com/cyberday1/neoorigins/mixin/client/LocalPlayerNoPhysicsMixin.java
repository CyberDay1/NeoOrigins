package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientActivePowers;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-side noclip for the {@code "no_physics"} capability tag.
 *
 * <p>Powers like {@code WraithPhasePower} and {@code PhantomFormPower} set
 * {@code player.noPhysics = true} on the server, but that field is NOT synced
 * via entity data — the client's {@code LocalPlayer} does its own movement
 * prediction and collision checks with {@code noPhysics = false}, causing
 * rubber-banding when the player tries to walk through blocks.
 *
 * <p>This mixin sets {@code noPhysics} on the client each tick based on
 * whether the {@code "no_physics"} capability is present in the synced
 * active-powers set.
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerNoPhysicsMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void neoorigins$syncNoPhysics(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        self.noPhysics = ClientActivePowers.hasCapability("no_physics");
    }
}
