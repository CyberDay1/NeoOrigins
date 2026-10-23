package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientGlowingEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws the glowing outline on the entities the local player's
 * {@code neoorigins:entity_glow} power selects, for that player only.
 * {@code Minecraft.shouldEntityAppearGlowing} is the per-entity test the level
 * renderer asks before the outline pass, and the seam Apoli's own
 * {@code entity_glow} hooks. The verdict comes from the server (see
 * {@code EntityGlowPower}), so this is a set lookup.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftEntityGlowMixin {

    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void neoorigins$entityGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (ClientGlowingEntities.isGlowing(entity.getId())) cir.setReturnValue(true);
    }
}
