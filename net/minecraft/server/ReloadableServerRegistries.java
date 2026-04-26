package net.minecraft.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.slf4j.Logger;

public class ReloadableServerRegistries {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static final RegistrationInfo DEFAULT_REGISTRATION_INFO = new RegistrationInfo(Optional.empty(), Lifecycle.experimental());

    public static CompletableFuture<LayeredRegistryAccess<RegistryLayer>> reload(
        LayeredRegistryAccess<RegistryLayer> p_335950_, ResourceManager p_335786_, Executor p_335516_
    ) {
        RegistryAccess.Frozen registryaccess$frozen = p_335950_.getAccessForLoading(RegistryLayer.RELOADABLE);
        RegistryOps<JsonElement> registryops = new ReloadableServerRegistries.EmptyTagLookupWrapper(registryaccess$frozen)
            .createSerializationContext(JsonOps.INSTANCE);
        List<CompletableFuture<WritableRegistry<?>>> list = LootDataType.values()
            .map(p_335899_ -> scheduleElementParse((LootDataType<?>)p_335899_, registryops, p_335786_, p_335516_))
            .toList();
        CompletableFuture<List<WritableRegistry<?>>> completablefuture = Util.sequence(list);
        return completablefuture.thenApplyAsync(p_335383_ -> apply(p_335950_, (List<WritableRegistry<?>>)p_335383_), p_335516_);
    }

    private static <T> CompletableFuture<WritableRegistry<?>> scheduleElementParse(
        LootDataType<T> p_335741_, RegistryOps<JsonElement> p_336173_, ResourceManager p_335893_, Executor p_336104_
    ) {
        return CompletableFuture.supplyAsync(
            () -> {
                WritableRegistry<T> writableregistry = new MappedRegistry<>(p_335741_.registryKey(), Lifecycle.experimental());
                Map<ResourceLocation, JsonElement> map = new HashMap<>();
                String s = Registries.elementsDirPath(p_335741_.registryKey());
                SimpleJsonResourceReloadListener.scanDirectory(p_335893_, s, GSON, map);
                map.forEach(
                    (p_335614_, p_335474_) -> p_335741_.deserialize(p_335614_, p_336173_, p_335474_)
                            .ifPresent(
                                p_335683_ -> writableregistry.register(
                                        ResourceKey.create(p_335741_.registryKey(), p_335614_), (T)p_335683_, DEFAULT_REGISTRATION_INFO
                                    )
                            )
                );
                return writableregistry;
            },
            p_336104_
        );
    }

    private static LayeredRegistryAccess<RegistryLayer> apply(LayeredRegistryAccess<RegistryLayer> p_335982_, List<WritableRegistry<?>> p_336159_) {
        LayeredRegistryAccess<RegistryLayer> layeredregistryaccess = createUpdatedRegistries(p_335982_, p_336159_);
        ProblemReporter.Collector problemreporter$collector = new ProblemReporter.Collector();
        RegistryAccess.Frozen registryaccess$frozen = layeredregistryaccess.compositeAccess();
        ValidationContext validationcontext = new ValidationContext(
            problemreporter$collector, LootContextParamSets.ALL_PARAMS, registryaccess$frozen.asGetterLookup()
        );
        LootDataType.values().forEach(p_336006_ -> validateRegistry(validationcontext, (LootDataType<?>)p_336006_, registryaccess$frozen));
        problemreporter$collector.get()
            .forEach((p_336001_, p_335424_) -> LOGGER.warn("Found loot table element validation problem in {}: {}", p_336001_, p_335424_));
        return layeredregistryaccess;
    }

    private static LayeredRegistryAccess<RegistryLayer> createUpdatedRegistries(
        LayeredRegistryAccess<RegistryLayer> p_335434_, List<WritableRegistry<?>> p_336097_
    ) {
        RegistryAccess registryaccess = new RegistryAccess.ImmutableRegistryAccess(p_336097_);
        ((WritableRegistry)registryaccess.<LootTable>registryOrThrow(Registries.LOOT_TABLE))
            .register(BuiltInLootTables.EMPTY, LootTable.EMPTY, DEFAULT_REGISTRATION_INFO);
        return p_335434_.replaceFrom(RegistryLayer.RELOADABLE, registryaccess.freeze());
    }

    private static <T> void validateRegistry(ValidationContext p_335565_, LootDataType<T> p_335997_, RegistryAccess p_335400_) {
        Registry<T> registry = p_335400_.registryOrThrow(p_335997_.registryKey());
        registry.holders().forEach(p_335842_ -> p_335997_.runValidation(p_335565_, p_335842_.key(), p_335842_.value()));
    }

    static class EmptyTagLookupWrapper implements HolderLookup.Provider {
        private final RegistryAccess registryAccess;

        EmptyTagLookupWrapper(RegistryAccess p_335912_) {
            this.registryAccess = p_335912_;
        }

        @Override
        public Stream<ResourceKey<? extends Registry<?>>> listRegistries() {
            return this.registryAccess.listRegistries();
        }

        @Override
        public <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> p_335976_) {
            return this.registryAccess.registry(p_335976_).map(Registry::asTagAddingLookup);
        }
    }

    public static class Holder {
        private final RegistryAccess.Frozen registries;

        public Holder(RegistryAccess.Frozen p_336133_) {
            this.registries = p_336133_;
        }

        public RegistryAccess.Frozen get() {
            return this.registries;
        }

        public HolderGetter.Provider lookup() {
            return this.registries.asGetterLookup();
        }

        public Collection<ResourceLocation> getKeys(ResourceKey<? extends Registry<?>> p_335695_) {
            return this.registries.registry(p_335695_).stream().flatMap(p_335639_ -> p_335639_.holders().map(p_335523_ -> p_335523_.key().location())).toList();
        }

        public LootTable getLootTable(ResourceKey<LootTable> p_335504_) {
            return this.registries
                .lookup(Registries.LOOT_TABLE)
                .flatMap(p_335799_ -> p_335799_.get(p_335504_))
                .map(net.minecraft.core.Holder::value)
                .orElse(LootTable.EMPTY);
        }
    }
}
