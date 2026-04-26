package net.minecraft.world.entity.animal;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

public class WolfVariants {
    public static final ResourceKey<WolfVariant> PALE = createKey("pale");
    public static final ResourceKey<WolfVariant> SPOTTED = createKey("spotted");
    public static final ResourceKey<WolfVariant> SNOWY = createKey("snowy");
    public static final ResourceKey<WolfVariant> BLACK = createKey("black");
    public static final ResourceKey<WolfVariant> ASHEN = createKey("ashen");
    public static final ResourceKey<WolfVariant> RUSTY = createKey("rusty");
    public static final ResourceKey<WolfVariant> WOODS = createKey("woods");
    public static final ResourceKey<WolfVariant> CHESTNUT = createKey("chestnut");
    public static final ResourceKey<WolfVariant> STRIPED = createKey("striped");
    public static final ResourceKey<WolfVariant> DEFAULT = PALE;

    private static ResourceKey<WolfVariant> createKey(String p_332764_) {
        return ResourceKey.create(Registries.WOLF_VARIANT, ResourceLocation.withDefaultNamespace(p_332764_));
    }

    static void register(BootstrapContext<WolfVariant> p_332703_, ResourceKey<WolfVariant> p_332747_, String p_332786_, ResourceKey<Biome> p_332693_) {
        register(p_332703_, p_332747_, p_332786_, HolderSet.direct(p_332703_.lookup(Registries.BIOME).getOrThrow(p_332693_)));
    }

    static void register(BootstrapContext<WolfVariant> p_333957_, ResourceKey<WolfVariant> p_334052_, String p_333903_, TagKey<Biome> p_333877_) {
        register(p_333957_, p_334052_, p_333903_, p_333957_.lookup(Registries.BIOME).getOrThrow(p_333877_));
    }

    static void register(BootstrapContext<WolfVariant> p_333853_, ResourceKey<WolfVariant> p_333874_, String p_333924_, HolderSet<Biome> p_333998_) {
        ResourceLocation resourcelocation = ResourceLocation.withDefaultNamespace("entity/wolf/" + p_333924_);
        ResourceLocation resourcelocation1 = ResourceLocation.withDefaultNamespace("entity/wolf/" + p_333924_ + "_tame");
        ResourceLocation resourcelocation2 = ResourceLocation.withDefaultNamespace("entity/wolf/" + p_333924_ + "_angry");
        p_333853_.register(p_333874_, new WolfVariant(resourcelocation, resourcelocation1, resourcelocation2, p_333998_));
    }

    public static Holder<WolfVariant> getSpawnVariant(RegistryAccess p_332694_, Holder<Biome> p_332773_) {
        Registry<WolfVariant> registry = p_332694_.registryOrThrow(Registries.WOLF_VARIANT);
        return registry.holders()
            .filter(p_332674_ -> p_332674_.value().biomes().contains(p_332773_))
            .findFirst()
            .or(() -> registry.getHolder(DEFAULT))
            .or(registry::getAny)
            .orElseThrow();
    }

    public static void bootstrap(BootstrapContext<WolfVariant> p_332726_) {
        register(p_332726_, PALE, "wolf", Biomes.TAIGA);
        register(p_332726_, SPOTTED, "wolf_spotted", BiomeTags.IS_SAVANNA);
        register(p_332726_, SNOWY, "wolf_snowy", Biomes.GROVE);
        register(p_332726_, BLACK, "wolf_black", Biomes.OLD_GROWTH_PINE_TAIGA);
        register(p_332726_, ASHEN, "wolf_ashen", Biomes.SNOWY_TAIGA);
        register(p_332726_, RUSTY, "wolf_rusty", BiomeTags.IS_JUNGLE);
        register(p_332726_, WOODS, "wolf_woods", Biomes.FOREST);
        register(p_332726_, CHESTNUT, "wolf_chestnut", Biomes.OLD_GROWTH_SPRUCE_TAIGA);
        register(p_332726_, STRIPED, "wolf_striped", BiomeTags.IS_BADLANDS);
    }
}
