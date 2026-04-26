package net.minecraft.tags;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

public class TagManager implements PreparableReloadListener {
    private final RegistryAccess registryAccess;
    private List<TagManager.LoadResult<?>> results = List.of();

    public TagManager(RegistryAccess p_144572_) {
        this.registryAccess = p_144572_;
    }

    public List<TagManager.LoadResult<?>> getResult() {
        return this.results;
    }

    @Override
    public CompletableFuture<Void> reload(
        PreparableReloadListener.PreparationBarrier p_13482_,
        ResourceManager p_13483_,
        ProfilerFiller p_13484_,
        ProfilerFiller p_13485_,
        Executor p_13486_,
        Executor p_13487_
    ) {
        List<? extends CompletableFuture<? extends TagManager.LoadResult<?>>> list = this.registryAccess
            .registries()
            .map(p_203927_ -> this.createLoader(p_13483_, p_13486_, (RegistryAccess.RegistryEntry<?>)p_203927_))
            .toList();
        return CompletableFuture.allOf(list.toArray(CompletableFuture[]::new))
            .thenCompose(p_13482_::wait)
            .thenAcceptAsync(p_203917_ -> this.results = list.stream().map(CompletableFuture::join).collect(Collectors.toUnmodifiableList()), p_13487_);
    }

    private <T> CompletableFuture<TagManager.LoadResult<T>> createLoader(
        ResourceManager p_203908_, Executor p_203909_, RegistryAccess.RegistryEntry<T> p_203910_
    ) {
        ResourceKey<? extends Registry<T>> resourcekey = p_203910_.key();
        Registry<T> registry = p_203910_.value();
        TagLoader<Holder<T>> tagloader = new TagLoader<>(registry::getHolder, Registries.tagsDirPath(resourcekey));
        return CompletableFuture.supplyAsync(() -> new TagManager.LoadResult<>(resourcekey, tagloader.loadAndBuild(p_203908_)), p_203909_);
    }

    public static record LoadResult<T>(ResourceKey<? extends Registry<T>> key, Map<ResourceLocation, Collection<Holder<T>>> tags) {
    }
}
