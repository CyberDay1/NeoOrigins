package net.minecraft.data.registries;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Cloner;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class RegistryPatchGenerator {
    public static CompletableFuture<RegistrySetBuilder.PatchedRegistries> createLookup(
        CompletableFuture<HolderLookup.Provider> p_309204_, RegistrySetBuilder p_309174_
    ) {
        return p_309204_.thenApply(
            p_311522_ -> {
                RegistryAccess.Frozen registryaccess$frozen = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
                Cloner.Factory cloner$factory = new Cloner.Factory();
                net.neoforged.neoforge.registries.DataPackRegistriesHooks.getDataPackRegistriesWithDimensions().forEach(p_311524_ -> p_311524_.runWithArguments(cloner$factory::addCodec));
                RegistrySetBuilder.PatchedRegistries registrysetbuilder$patchedregistries = p_309174_.buildPatch(
                    registryaccess$frozen, p_311522_, cloner$factory
                );
                HolderLookup.Provider holderlookup$provider = registrysetbuilder$patchedregistries.full();
                Optional<HolderLookup.RegistryLookup<Biome>> optional = holderlookup$provider.lookup(Registries.BIOME);
                Optional<HolderLookup.RegistryLookup<PlacedFeature>> optional1 = holderlookup$provider.lookup(Registries.PLACED_FEATURE);
                if (optional.isPresent() || optional1.isPresent()) {
                    VanillaRegistries.validateThatAllBiomeFeaturesHaveBiomeFilter(
                        optional1.orElseGet(() -> p_311522_.lookupOrThrow(Registries.PLACED_FEATURE)),
                        optional.orElseGet(() -> p_311522_.lookupOrThrow(Registries.BIOME))
                    );
                }

                return registrysetbuilder$patchedregistries;
            }
        );
    }
}
