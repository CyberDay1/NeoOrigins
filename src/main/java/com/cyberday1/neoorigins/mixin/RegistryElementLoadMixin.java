package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.compat.OriginpackLoadGuard;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Decoder;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An originpack element that fails to parse is skipped with a warning instead of failing the whole load. */
@Mixin(targets = "net.minecraft.resources.RegistryLoadTask$PendingRegistration")
public abstract class RegistryElementLoadMixin {

    @Inject(method = "loadFromResource", at = @At("RETURN"), cancellable = true)
    private static void neoorigins$skipBrokenOriginpackElement(Decoder<?> decoder, RegistryOps<?> ops, ResourceKey<?> key,
                                                               Resource thunk, CallbackInfoReturnable<Either<Object, Exception>> cir) {
        Exception error = cir.getReturnValue().right().orElse(null);
        if (error == null || error == RegistryLoadTaskAccessor.neoorigins$skippedElementMarker()) return;
        if (OriginpackLoadGuard.skip(thunk.sourcePackId(), key, error)) {
            cir.setReturnValue(Either.right(RegistryLoadTaskAccessor.neoorigins$skippedElementMarker()));
        }
    }
}
