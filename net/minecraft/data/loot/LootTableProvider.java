package net.minecraft.data.loot;

import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.RandomSequence;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.slf4j.Logger;

public class LootTableProvider implements DataProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final PackOutput.PathProvider pathProvider;
    private final Set<ResourceKey<LootTable>> requiredTables;
    private final List<LootTableProvider.SubProviderEntry> subProviders;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public LootTableProvider(
        PackOutput p_254123_,
        Set<ResourceKey<LootTable>> p_254481_,
        List<LootTableProvider.SubProviderEntry> p_253798_,
        CompletableFuture<HolderLookup.Provider> p_323798_
    ) {
        this.pathProvider = p_254123_.createRegistryElementsPathProvider(Registries.LOOT_TABLE);
        this.subProviders = p_253798_;
        this.requiredTables = p_254481_;
        this.registries = p_323798_;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput p_254060_) {
        return this.registries.thenCompose(p_323117_ -> this.run(p_254060_, p_323117_));
    }

    private CompletableFuture<?> run(CachedOutput p_324447_, HolderLookup.Provider p_323978_) {
        WritableRegistry<LootTable> writableregistry = new MappedRegistry<>(Registries.LOOT_TABLE, Lifecycle.experimental());
        Map<RandomSupport.Seed128bit, ResourceLocation> map = new Object2ObjectOpenHashMap<>();
        getTables().forEach(p_329847_ -> p_329847_.provider().apply(p_323978_).generate((p_335199_, p_335200_) -> {
                ResourceLocation resourcelocation = sequenceIdForLootTable(p_335199_);
                ResourceLocation resourcelocation1 = map.put(RandomSequence.seedForKey(resourcelocation), resourcelocation);
                if (resourcelocation1 != null) {
                    Util.logAndPauseIfInIde("Loot table random sequence seed collision on " + resourcelocation1 + " and " + p_335199_.location());
                }

                p_335200_.setRandomSequence(resourcelocation);
                LootTable loottable = p_335200_.setParamSet(p_329847_.paramSet).build();
                writableregistry.register(p_335199_, loottable, RegistrationInfo.BUILT_IN);
            }));
        writableregistry.freeze();
        ProblemReporter.Collector problemreporter$collector = new ProblemReporter.Collector();
        HolderGetter.Provider holdergetter$provider = new RegistryAccess.ImmutableRegistryAccess(List.of(writableregistry)).freeze().asGetterLookup();
        ValidationContext validationcontext = new ValidationContext(problemreporter$collector, LootContextParamSets.ALL_PARAMS, holdergetter$provider);

        validate(writableregistry, validationcontext, problemreporter$collector);

        Multimap<String, String> multimap = problemreporter$collector.get();
        if (!multimap.isEmpty()) {
            multimap.forEach((p_124446_, p_124447_) -> LOGGER.warn("Found validation problem in {}: {}", p_124446_, p_124447_));
            throw new IllegalStateException("Failed to validate loot tables, see logs");
        } else {
            return CompletableFuture.allOf(writableregistry.entrySet().stream().map(p_335193_ -> {
                ResourceKey<LootTable> resourcekey1 = p_335193_.getKey();
                LootTable loottable = p_335193_.getValue();
                Path path = this.pathProvider.json(resourcekey1.location());
                return DataProvider.saveStable(p_324447_, p_323978_, LootTable.DIRECT_CODEC, loottable, path);
            }).toArray(CompletableFuture[]::new));
        }
    }

    public List<LootTableProvider.SubProviderEntry> getTables() {
        return this.subProviders;
    }

    protected void validate(WritableRegistry<LootTable> writableregistry, ValidationContext validationcontext, ProblemReporter.Collector problemreporter$collector) {
        for(ResourceKey<LootTable> resourcekey : Sets.difference(this.requiredTables, writableregistry.registryKeySet())) {
            problemreporter$collector.report("Missing built-in table: " + resourcekey.location());
        }

        writableregistry.holders().forEach(p_335195_ -> {
            p_335195_.value().validate(validationcontext.setParams(p_335195_.value().getParamSet()).enterElement("{" + p_335195_.key().location() + "}", p_335195_.key()));
        });
    }

    private static ResourceLocation sequenceIdForLootTable(ResourceKey<LootTable> p_336172_) {
        return p_336172_.location();
    }

    @Override
    public final String getName() {
        return "Loot Tables";
    }

    public static record SubProviderEntry(Function<HolderLookup.Provider, LootTableSubProvider> provider, LootContextParamSet paramSet) {
    }
}
