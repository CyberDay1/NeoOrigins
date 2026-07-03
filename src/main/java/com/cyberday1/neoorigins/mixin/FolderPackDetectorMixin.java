package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.compat.LegacyFolderPackResources;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wraps every pack discovered by vanilla's folder scanner (world
 * {@code datapacks/}, global pack folders) in
 * {@link LegacyFolderPackResources}, so 1.20-era Origins/Apoli datapacks
 * dropped into a world as-is get their plural data folders ({@code functions/},
 * {@code recipes/}, {@code tags/items/}, ...) served under the modern 1.21
 * singular names. Companion hook to
 * {@link com.cyberday1.neoorigins.compat.OriginsPackFinder}, which does the
 * same wrap for originpacks/ at creation.
 */
@Mixin(targets = "net.minecraft.server.packs.repository.FolderRepositorySource$FolderPackDetector")
public abstract class FolderPackDetectorMixin {

    @Inject(method = "createZipPack(Ljava/nio/file/Path;)Lnet/minecraft/server/packs/repository/Pack$ResourcesSupplier;",
            at = @At("RETURN"), cancellable = true)
    private void neoorigins$wrapZipPack(CallbackInfoReturnable<Pack.ResourcesSupplier> cir) {
        if (cir.getReturnValue() != null) {
            cir.setReturnValue(LegacyFolderPackResources.wrap(cir.getReturnValue()));
        }
    }

    @Inject(method = "createDirectoryPack(Ljava/nio/file/Path;)Lnet/minecraft/server/packs/repository/Pack$ResourcesSupplier;",
            at = @At("RETURN"), cancellable = true)
    private void neoorigins$wrapDirectoryPack(CallbackInfoReturnable<Pack.ResourcesSupplier> cir) {
        if (cir.getReturnValue() != null) {
            cir.setReturnValue(LegacyFolderPackResources.wrap(cir.getReturnValue()));
        }
    }
}
