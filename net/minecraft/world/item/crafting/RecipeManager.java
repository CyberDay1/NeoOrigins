package net.minecraft.world.item.crafting;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.ImmutableMultimap.Builder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public class RecipeManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Logger LOGGER = LogUtils.getLogger();
    private final HolderLookup.Provider registries;
    private Multimap<RecipeType<?>, RecipeHolder<?>> byType = ImmutableMultimap.of();
    private Map<ResourceLocation, RecipeHolder<?>> byName = ImmutableMap.of();
    private boolean hasErrors;

    public RecipeManager(HolderLookup.Provider p_324137_) {
        super(GSON, Registries.elementsDirPath(Registries.RECIPE));
        this.registries = p_324137_;
    }

    protected void apply(Map<ResourceLocation, JsonElement> p_44037_, ResourceManager p_44038_, ProfilerFiller p_44039_) {
        this.hasErrors = false;
        Builder<RecipeType<?>, RecipeHolder<?>> builder = ImmutableMultimap.builder();
        com.google.common.collect.ImmutableMap.Builder<ResourceLocation, RecipeHolder<?>> builder1 = ImmutableMap.builder();
        RegistryOps<JsonElement> registryops = this.makeConditionalOps(); // Neo: add condition context

        for (Entry<ResourceLocation, JsonElement> entry : p_44037_.entrySet()) {
            ResourceLocation resourcelocation = entry.getKey();
            if (resourcelocation.getPath().startsWith("_")) continue; //Forge: filter anything beginning with "_" as it's used for metadata.

            try {
                var decoded = Recipe.CONDITIONAL_CODEC.parse(registryops, entry.getValue()).getOrThrow(JsonParseException::new);
                decoded.ifPresentOrElse(r -> {
                Recipe<?> recipe = r.carrier();
                RecipeHolder<?> recipeholder = new RecipeHolder<>(resourcelocation, recipe);
                builder.put(recipe.getType(), recipeholder);
                builder1.put(resourcelocation, recipeholder);
                }, () -> {
                    LOGGER.debug("Skipping loading recipe {} as its conditions were not met", resourcelocation);
                });
            } catch (IllegalArgumentException | JsonParseException jsonparseexception) {
                LOGGER.error("Parsing error loading recipe {}", resourcelocation, jsonparseexception);
            }
        }

        this.byType = builder.build();
        this.byName = builder1.build();
        LOGGER.info("Loaded {} recipes", this.byType.size());
    }

    public boolean hadErrorsLoading() {
        return this.hasErrors;
    }

    public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(RecipeType<T> p_44016_, I p_345492_, Level p_44018_) {
        return this.getRecipeFor(p_44016_, p_345492_, p_44018_, (RecipeHolder<T>)null);
    }

    public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(
        RecipeType<T> p_345895_, I p_345268_, Level p_346336_, @Nullable ResourceLocation p_346260_
    ) {
        RecipeHolder<T> recipeholder = p_346260_ != null ? this.byKeyTyped(p_345895_, p_346260_) : null;
        return this.getRecipeFor(p_345895_, p_345268_, p_346336_, recipeholder);
    }

    public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(
        RecipeType<T> p_220249_, I p_345826_, Level p_220251_, @Nullable RecipeHolder<T> p_346407_
    ) {
        if (p_345826_.isEmpty()) {
            return Optional.empty();
        } else {
            return p_346407_ != null && p_346407_.value().matches(p_345826_, p_220251_)
                ? Optional.of(p_346407_)
                : this.byType(p_220249_).stream().filter(p_344413_ -> p_344413_.value().matches(p_345826_, p_220251_)).findFirst();
        }
    }

    public <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<T>> getAllRecipesFor(RecipeType<T> p_44014_) {
        return List.copyOf(this.byType(p_44014_));
    }

    public <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<T>> getRecipesFor(RecipeType<T> p_44057_, I p_346353_, Level p_44059_) {
        return this.byType(p_44057_)
            .stream()
            .filter(p_344410_ -> p_344410_.value().matches(p_346353_, p_44059_))
            .sorted(Comparator.comparing(p_335290_ -> p_335290_.value().getResultItem(p_44059_.registryAccess()).getDescriptionId()))
            .collect(Collectors.toList());
    }

    private <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> byType(RecipeType<T> p_44055_) {
        return (Collection<RecipeHolder<T>>)(Collection<?>)this.byType.get(p_44055_);
    }

    public <I extends RecipeInput, T extends Recipe<I>> NonNullList<ItemStack> getRemainingItemsFor(RecipeType<T> p_44070_, I p_345118_, Level p_44072_) {
        Optional<RecipeHolder<T>> optional = this.getRecipeFor(p_44070_, p_345118_, p_44072_);
        if (optional.isPresent()) {
            return optional.get().value().getRemainingItems(p_345118_);
        } else {
            NonNullList<ItemStack> nonnulllist = NonNullList.withSize(p_345118_.size(), ItemStack.EMPTY);

            for (int i = 0; i < nonnulllist.size(); i++) {
                nonnulllist.set(i, p_345118_.getItem(i));
            }

            return nonnulllist;
        }
    }

    public Optional<RecipeHolder<?>> byKey(ResourceLocation p_44044_) {
        return Optional.ofNullable(this.byName.get(p_44044_));
    }

    @Nullable
    private <T extends Recipe<?>> RecipeHolder<T> byKeyTyped(RecipeType<T> p_341695_, ResourceLocation p_341666_) {
        RecipeHolder<?> recipeholder = this.byName.get(p_341666_);
        return (RecipeHolder<T>)(recipeholder != null && recipeholder.value().getType().equals(p_341695_) ? recipeholder : null);
    }

    public Collection<RecipeHolder<?>> getOrderedRecipes() {
        return this.byType.values();
    }

    public Collection<RecipeHolder<?>> getRecipes() {
        return this.byName.values();
    }

    public Stream<ResourceLocation> getRecipeIds() {
        return this.byName.keySet().stream();
    }

    @VisibleForTesting
    protected static RecipeHolder<?> fromJson(ResourceLocation p_44046_, JsonObject p_44047_, HolderLookup.Provider p_323755_) {
        Recipe<?> recipe = Recipe.CODEC.parse(p_323755_.createSerializationContext(JsonOps.INSTANCE), p_44047_).getOrThrow(JsonParseException::new);
        return new RecipeHolder<>(p_44046_, recipe);
    }

    public void replaceRecipes(Iterable<RecipeHolder<?>> p_44025_) {
        this.hasErrors = false;
        Builder<RecipeType<?>, RecipeHolder<?>> builder = ImmutableMultimap.builder();
        com.google.common.collect.ImmutableMap.Builder<ResourceLocation, RecipeHolder<?>> builder1 = ImmutableMap.builder();

        for (RecipeHolder<?> recipeholder : p_44025_) {
            RecipeType<?> recipetype = recipeholder.value().getType();
            builder.put(recipetype, recipeholder);
            builder1.put(recipeholder.id(), recipeholder);
        }

        this.byType = builder.build();
        this.byName = builder1.build();
    }

    public static <I extends RecipeInput, T extends Recipe<I>> RecipeManager.CachedCheck<I, T> createCheck(final RecipeType<T> p_220268_) {
        return new RecipeManager.CachedCheck<I, T>() {
            @Nullable
            private ResourceLocation lastRecipe;

            @Override
            public Optional<RecipeHolder<T>> getRecipeFor(I p_344742_, Level p_220279_) {
                RecipeManager recipemanager = p_220279_.getRecipeManager();
                Optional<RecipeHolder<T>> optional = recipemanager.getRecipeFor(p_220268_, p_344742_, p_220279_, this.lastRecipe);
                if (optional.isPresent()) {
                    RecipeHolder<T> recipeholder = optional.get();
                    this.lastRecipe = recipeholder.id();
                    return Optional.of(recipeholder);
                } else {
                    return Optional.empty();
                }
            }
        };
    }

    public interface CachedCheck<I extends RecipeInput, T extends Recipe<I>> {
        Optional<RecipeHolder<T>> getRecipeFor(I p_344938_, Level p_220281_);
    }
}
