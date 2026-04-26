package net.minecraft.world.item.armortrim;

import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TrimPatterns {
    public static final ResourceKey<TrimPattern> SENTRY = registryKey("sentry");
    public static final ResourceKey<TrimPattern> DUNE = registryKey("dune");
    public static final ResourceKey<TrimPattern> COAST = registryKey("coast");
    public static final ResourceKey<TrimPattern> WILD = registryKey("wild");
    public static final ResourceKey<TrimPattern> WARD = registryKey("ward");
    public static final ResourceKey<TrimPattern> EYE = registryKey("eye");
    public static final ResourceKey<TrimPattern> VEX = registryKey("vex");
    public static final ResourceKey<TrimPattern> TIDE = registryKey("tide");
    public static final ResourceKey<TrimPattern> SNOUT = registryKey("snout");
    public static final ResourceKey<TrimPattern> RIB = registryKey("rib");
    public static final ResourceKey<TrimPattern> SPIRE = registryKey("spire");
    public static final ResourceKey<TrimPattern> WAYFINDER = registryKey("wayfinder");
    public static final ResourceKey<TrimPattern> SHAPER = registryKey("shaper");
    public static final ResourceKey<TrimPattern> SILENCE = registryKey("silence");
    public static final ResourceKey<TrimPattern> RAISER = registryKey("raiser");
    public static final ResourceKey<TrimPattern> HOST = registryKey("host");
    public static final ResourceKey<TrimPattern> FLOW = registryKey("flow");
    public static final ResourceKey<TrimPattern> BOLT = registryKey("bolt");

    public static void bootstrap(BootstrapContext<TrimPattern> p_321567_) {
        register(p_321567_, Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, SENTRY);
        register(p_321567_, Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, DUNE);
        register(p_321567_, Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, COAST);
        register(p_321567_, Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, WILD);
        register(p_321567_, Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, WARD);
        register(p_321567_, Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE, EYE);
        register(p_321567_, Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, VEX);
        register(p_321567_, Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, TIDE);
        register(p_321567_, Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, SNOUT);
        register(p_321567_, Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE, RIB);
        register(p_321567_, Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, SPIRE);
        register(p_321567_, Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, WAYFINDER);
        register(p_321567_, Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, SHAPER);
        register(p_321567_, Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, SILENCE);
        register(p_321567_, Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, RAISER);
        register(p_321567_, Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, HOST);
        register(p_321567_, Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, FLOW);
        register(p_321567_, Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, BOLT);
    }

    public static Optional<Holder.Reference<TrimPattern>> getFromTemplate(HolderLookup.Provider p_335972_, ItemStack p_267001_) {
        return p_335972_.lookupOrThrow(Registries.TRIM_PATTERN).listElements().filter(p_266833_ -> p_267001_.is(p_266833_.value().templateItem())).findFirst();
    }

    public static void register(BootstrapContext<TrimPattern> p_321509_, Item p_267097_, ResourceKey<TrimPattern> p_267079_) {
        TrimPattern trimpattern = new TrimPattern(
            p_267079_.location(),
            BuiltInRegistries.ITEM.wrapAsHolder(p_267097_),
            Component.translatable(Util.makeDescriptionId("trim_pattern", p_267079_.location())),
            false
        );
        p_321509_.register(p_267079_, trimpattern);
    }

    private static ResourceKey<TrimPattern> registryKey(String p_266889_) {
        return ResourceKey.create(Registries.TRIM_PATTERN, ResourceLocation.withDefaultNamespace(p_266889_));
    }
}
