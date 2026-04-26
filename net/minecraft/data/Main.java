package net.minecraft.data;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.WorldVersion;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.data.advancements.packs.VanillaAdvancementProvider;
import net.minecraft.data.info.BiomeParametersDumpReport;
import net.minecraft.data.info.BlockListReport;
import net.minecraft.data.info.CommandsReport;
import net.minecraft.data.info.ItemListReport;
import net.minecraft.data.info.PacketReport;
import net.minecraft.data.info.RegistryDumpReport;
import net.minecraft.data.loot.packs.TradeRebalanceLootTableProvider;
import net.minecraft.data.loot.packs.VanillaLootTableProvider;
import net.minecraft.data.metadata.PackMetadataGenerator;
import net.minecraft.data.models.ModelProvider;
import net.minecraft.data.recipes.packs.BundleRecipeProvider;
import net.minecraft.data.recipes.packs.VanillaRecipeProvider;
import net.minecraft.data.registries.RegistriesDatapackGenerator;
import net.minecraft.data.registries.TradeRebalanceRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.data.structures.NbtToSnbt;
import net.minecraft.data.structures.SnbtToNbt;
import net.minecraft.data.structures.StructureUpdater;
import net.minecraft.data.tags.BannerPatternTagsProvider;
import net.minecraft.data.tags.BiomeTagsProvider;
import net.minecraft.data.tags.CatVariantTagsProvider;
import net.minecraft.data.tags.DamageTypeTagsProvider;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.data.tags.FlatLevelGeneratorPresetTagsProvider;
import net.minecraft.data.tags.FluidTagsProvider;
import net.minecraft.data.tags.GameEventTagsProvider;
import net.minecraft.data.tags.InstrumentTagsProvider;
import net.minecraft.data.tags.PaintingVariantTagsProvider;
import net.minecraft.data.tags.PoiTypeTagsProvider;
import net.minecraft.data.tags.StructureTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.data.tags.TradeRebalanceEnchantmentTagsProvider;
import net.minecraft.data.tags.TradeRebalanceStructureTagsProvider;
import net.minecraft.data.tags.VanillaBlockTagsProvider;
import net.minecraft.data.tags.VanillaEnchantmentTagsProvider;
import net.minecraft.data.tags.VanillaItemTagsProvider;
import net.minecraft.data.tags.WorldPresetTagsProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.obfuscate.DontObfuscate;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.levelgen.structure.Structure;

public class Main {
    @DontObfuscate
    public static void main(String[] p_129669_) throws IOException {
        SharedConstants.tryDetectVersion();
        OptionParser optionparser = new OptionParser();
        OptionSpec<Void> optionspec = optionparser.accepts("help", "Show the help menu").forHelp();
        OptionSpec<Void> optionspec1 = optionparser.accepts("server", "Include server generators");
        OptionSpec<Void> optionspec2 = optionparser.accepts("client", "Include client generators");
        OptionSpec<Void> optionspec3 = optionparser.accepts("dev", "Include development tools");
        OptionSpec<Void> optionspec4 = optionparser.accepts("reports", "Include data reports");
        OptionSpec<Void> optionspec5 = optionparser.accepts("validate", "Validate inputs");
        OptionSpec<Void> optionspec6 = optionparser.accepts("all", "Include all generators");
        OptionSpec<String> optionspec7 = optionparser.accepts("output", "Output folder").withRequiredArg().defaultsTo("generated");
        OptionSpec<String> optionspec8 = optionparser.accepts("input", "Input folder").withRequiredArg();
        OptionSpec<String> existing = optionparser.accepts("existing", "Existing resource packs that generated resources can reference").withRequiredArg();
        OptionSpec<String> existingMod = optionparser.accepts("existing-mod", "Existing mods that generated resources can reference the resource packs of").withRequiredArg();
        OptionSpec<java.io.File> gameDir = optionparser.accepts("gameDir").withRequiredArg().ofType(java.io.File.class).defaultsTo(new java.io.File(".")).required(); //Need by modlauncher, so lets just eat it
        OptionSpec<String> mod = optionparser.accepts("mod", "A modid to dump").withRequiredArg().withValuesSeparatedBy(",");
        OptionSpec<Void> flat = optionparser.accepts("flat", "Do not append modid prefix to output directory when generating for multiple mods");
        OptionSpec<String> assetIndex = optionparser.accepts("assetIndex").withRequiredArg();
        OptionSpec<java.io.File> assetsDir = optionparser.accepts("assetsDir").withRequiredArg().ofType(java.io.File.class);
        OptionSet optionset = optionparser.parse(p_129669_);
        if (!optionset.has(optionspec) && optionset.hasOptions() && !(optionset.specs().size() == 1 && optionset.has(gameDir))) {
            Path path = Paths.get(optionspec7.value(optionset));
            boolean flag = optionset.has(optionspec6);
            boolean flag1 = flag || optionset.has(optionspec2);
            boolean flag2 = flag || optionset.has(optionspec1);
            boolean flag3 = flag || optionset.has(optionspec3);
            boolean flag4 = flag || optionset.has(optionspec4);
            boolean flag5 = flag || optionset.has(optionspec5);
            Collection<Path> inputs = optionset.valuesOf(optionspec8).stream().map(Paths::get).collect(Collectors.toList());
            Collection<Path> existingPacks = optionset.valuesOf(existing).stream().map(Paths::get).collect(Collectors.toList());
            java.util.Set<String> existingMods = new java.util.HashSet<>(optionset.valuesOf(existingMod));
            java.util.Set<String> mods = new java.util.HashSet<>(optionset.valuesOf(mod));
            boolean isFlat = mods.isEmpty() || optionset.has(flat);
            net.neoforged.neoforge.data.loading.DatagenModLoader.begin(mods, path, inputs, existingPacks, existingMods, flag2, flag1, flag3, flag4, flag5, isFlat, optionset.valueOf(assetIndex), optionset.valueOf(assetsDir));
            if (mods.contains("minecraft") || mods.isEmpty()) {
            DataGenerator datagenerator = createStandardGenerator(
                isFlat ? path : path.resolve("minecraft"),
                inputs,
                flag1,
                flag2,
                flag3,
                flag4,
                flag5,
                SharedConstants.getCurrentVersion(),
                true
            );
            datagenerator.run();
            }
        } else {
            optionparser.printHelpOn(System.out);
        }
    }

    private static <T extends DataProvider> DataProvider.Factory<T> bindRegistries(
        BiFunction<PackOutput, CompletableFuture<HolderLookup.Provider>, T> p_256618_, CompletableFuture<HolderLookup.Provider> p_256515_
    ) {
        return p_255476_ -> p_256618_.apply(p_255476_, p_256515_);
    }

    public static DataGenerator createStandardGenerator(
        Path p_236680_,
        Collection<Path> p_236681_,
        boolean p_236682_,
        boolean p_236683_,
        boolean p_236684_,
        boolean p_236685_,
        boolean p_236686_,
        WorldVersion p_236687_,
        boolean p_236688_
    ) {
        DataGenerator datagenerator = new DataGenerator(p_236680_, p_236687_, p_236688_);
        DataGenerator.PackGenerator datagenerator$packgenerator = datagenerator.getVanillaPack(p_236682_ || p_236683_);
        datagenerator$packgenerator.addProvider(p_253388_ -> new SnbtToNbt(p_253388_, p_236681_).addFilter(new StructureUpdater()));
        CompletableFuture<HolderLookup.Provider> completablefuture1 = CompletableFuture.supplyAsync(VanillaRegistries::createLookup, Util.backgroundExecutor());
        DataGenerator.PackGenerator datagenerator$packgenerator1 = datagenerator.getVanillaPack(p_236682_);
        datagenerator$packgenerator1.addProvider(ModelProvider::new);
        DataGenerator.PackGenerator datagenerator$packgenerator2 = datagenerator.getVanillaPack(p_236683_);
        datagenerator$packgenerator2.addProvider(bindRegistries(RegistriesDatapackGenerator::new, completablefuture1));
        datagenerator$packgenerator2.addProvider(bindRegistries(VanillaAdvancementProvider::create, completablefuture1));
        datagenerator$packgenerator2.addProvider(bindRegistries(VanillaLootTableProvider::create, completablefuture1));
        datagenerator$packgenerator2.addProvider(bindRegistries(VanillaRecipeProvider::new, completablefuture1));
        TagsProvider<Block> tagsprovider4 = datagenerator$packgenerator2.addProvider(bindRegistries(VanillaBlockTagsProvider::new, completablefuture1));
        TagsProvider<Item> tagsprovider = datagenerator$packgenerator2.addProvider(
            p_274753_ -> new VanillaItemTagsProvider(p_274753_, completablefuture1, tagsprovider4.contentsGetter())
        );
        TagsProvider<Biome> tagsprovider1 = datagenerator$packgenerator2.addProvider(bindRegistries(BiomeTagsProvider::new, completablefuture1));
        TagsProvider<BannerPattern> tagsprovider2 = datagenerator$packgenerator2.addProvider(bindRegistries(BannerPatternTagsProvider::new, completablefuture1));
        TagsProvider<Structure> tagsprovider3 = datagenerator$packgenerator2.addProvider(bindRegistries(StructureTagsProvider::new, completablefuture1));
        datagenerator$packgenerator2.addProvider(bindRegistries(CatVariantTagsProvider::new, completablefuture1));
        datagenerator$packgenerator2.addProvider(bindRegistries(DamageTypeTagsProvider::new, completablefuture1));
        datagenerator$packgenerator2.addProvider(bindRegistries(EntityTypeTagsProvider::new, completablefuture1));
        datagenerator$packgenerator2.addProvider(bindRegistries(FlatLevelGeneratorPresetTagsProvider::new, completablefuture1));
        datagenerator$packgenerator2.addProvider(bindRegistries(FluidTagsProvider::new, completablefuture1));
        datagenerator$packgenerator2.addProvider(bindRegistries(GameEventTagsProvider::new, completablefuture1));
        datagenerator$packgenerator2.addProvider(bindRegistries(InstrumentTagsProvider::new, completablefuture1));
        datagenerator$packgenerator2.addProvider(bindRegistries(PaintingVariantTagsProvider::new, completablefuture1));
        datagenerator$packgenerator2.addProvider(bindRegistries(PoiTypeTagsProvider::new, completablefuture1));
        datagenerator$packgenerator2.addProvider(bindRegistries(WorldPresetTagsProvider::new, completablefuture1));
        datagenerator$packgenerator2.addProvider(bindRegistries(VanillaEnchantmentTagsProvider::new, completablefuture1));
        datagenerator$packgenerator2 = datagenerator.getVanillaPack(p_236684_);
        datagenerator$packgenerator2.addProvider(p_253386_ -> new NbtToSnbt(p_253386_, p_236681_));
        datagenerator$packgenerator2 = datagenerator.getVanillaPack(p_236685_);
        datagenerator$packgenerator2.addProvider(bindRegistries(BiomeParametersDumpReport::new, completablefuture1));
        datagenerator$packgenerator2.addProvider(bindRegistries(ItemListReport::new, completablefuture1));
        datagenerator$packgenerator2.addProvider(bindRegistries(BlockListReport::new, completablefuture1));
        datagenerator$packgenerator2.addProvider(bindRegistries(CommandsReport::new, completablefuture1));
        datagenerator$packgenerator2.addProvider(RegistryDumpReport::new);
        datagenerator$packgenerator2.addProvider(PacketReport::new);
        datagenerator$packgenerator2 = datagenerator.getBuiltinDatapack(p_236683_, "bundle");
        datagenerator$packgenerator2.addProvider(bindRegistries(BundleRecipeProvider::new, completablefuture1));
        datagenerator$packgenerator2.addProvider(
            p_253392_ -> PackMetadataGenerator.forFeaturePack(
                    p_253392_, Component.translatable("dataPack.bundle.description"), FeatureFlagSet.of(FeatureFlags.BUNDLE)
                )
        );
        CompletableFuture<RegistrySetBuilder.PatchedRegistries> completablefuture2 = TradeRebalanceRegistries.createLookup(completablefuture1);
        CompletableFuture<HolderLookup.Provider> completablefuture = completablefuture2.thenApply(RegistrySetBuilder.PatchedRegistries::patches);
        DataGenerator.PackGenerator datagenerator$packgenerator3 = datagenerator.getBuiltinDatapack(p_236683_, "trade_rebalance");
        datagenerator$packgenerator3.addProvider(bindRegistries(RegistriesDatapackGenerator::new, completablefuture));
        datagenerator$packgenerator3.addProvider(
            p_293698_ -> PackMetadataGenerator.forFeaturePack(
                    p_293698_, Component.translatable("dataPack.trade_rebalance.description"), FeatureFlagSet.of(FeatureFlags.TRADE_REBALANCE)
                )
        );
        datagenerator$packgenerator3.addProvider(bindRegistries(TradeRebalanceLootTableProvider::create, completablefuture1));
        datagenerator$packgenerator3.addProvider(bindRegistries(TradeRebalanceStructureTagsProvider::new, completablefuture1));
        datagenerator$packgenerator3.addProvider(bindRegistries(TradeRebalanceEnchantmentTagsProvider::new, completablefuture1));
        return datagenerator;
    }
}
