package net.minecraft.client.multiplayer;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.searchtree.FullTextSearchTree;
import net.minecraft.client.searchtree.IdSearchTree;
import net.minecraft.client.searchtree.SearchTree;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SessionSearchTrees {
    private static final SessionSearchTrees.Key RECIPE_COLLECTIONS = new SessionSearchTrees.Key();
    public static final SessionSearchTrees.Key CREATIVE_NAMES = new SessionSearchTrees.Key();
    public static final SessionSearchTrees.Key CREATIVE_TAGS = new SessionSearchTrees.Key();
    private CompletableFuture<SearchTree<ItemStack>> creativeByNameSearch = CompletableFuture.completedFuture(SearchTree.empty());
    private CompletableFuture<SearchTree<ItemStack>> creativeByTagSearch = CompletableFuture.completedFuture(SearchTree.empty());
    private CompletableFuture<SearchTree<RecipeCollection>> recipeSearch = CompletableFuture.completedFuture(SearchTree.empty());
    private final Map<SessionSearchTrees.Key, Runnable> reloaders = new IdentityHashMap<>();

    private void register(SessionSearchTrees.Key p_345458_, Runnable p_346175_) {
        p_346175_.run();
        this.reloaders.put(p_345458_, p_346175_);
    }

    public void rebuildAfterLanguageChange() {
        for (Runnable runnable : this.reloaders.values()) {
            runnable.run();
        }
    }

    private static Stream<String> getTooltipLines(Stream<ItemStack> p_344879_, Item.TooltipContext p_345662_, TooltipFlag p_345262_) {
        return p_344879_.<Component>flatMap(p_344980_ -> p_344980_.getTooltipLines(p_345662_, null, p_345262_).stream())
            .map(p_345615_ -> ChatFormatting.stripFormatting(p_345615_.getString()).trim())
            .filter(p_346341_ -> !p_346341_.isEmpty());
    }

    public void updateRecipes(ClientRecipeBook p_346233_, RegistryAccess.Frozen p_345600_) {
        this.register(
            RECIPE_COLLECTIONS,
            () -> {
                List<RecipeCollection> list = p_346233_.getCollections();
                Registry<Item> registry = p_345600_.registryOrThrow(Registries.ITEM);
                Item.TooltipContext item$tooltipcontext = Item.TooltipContext.of(p_345600_);
                TooltipFlag tooltipflag = net.neoforged.neoforge.client.ClientTooltipFlag.of(TooltipFlag.Default.NORMAL);
                CompletableFuture<?> completablefuture = this.recipeSearch;
                this.recipeSearch = CompletableFuture.supplyAsync(
                    () -> new FullTextSearchTree<>(
                            p_346411_ -> getTooltipLines(
                                    p_346411_.getRecipes().stream().map(p_345321_ -> p_345321_.value().getResultItem(p_345600_)),
                                    item$tooltipcontext,
                                    tooltipflag
                                ),
                            p_345180_ -> p_345180_.getRecipes()
                                    .stream()
                                    .map(p_346084_ -> registry.getKey(p_346084_.value().getResultItem(p_345600_).getItem())),
                            list
                        ),
                    Util.backgroundExecutor()
                );
                completablefuture.cancel(true);
            }
        );
    }

    public SearchTree<RecipeCollection> recipes() {
        return this.recipeSearch.join();
    }

    public void updateCreativeTags(List<ItemStack> p_346121_) {
        this.updateCreativeTags(p_346121_, CREATIVE_TAGS);
    }

    public void updateCreativeTags(List<ItemStack> p_346121_, SessionSearchTrees.Key key) {
        this.register(
            key,
            () -> {
                CompletableFuture<?> completablefuture = net.neoforged.neoforge.client.CreativeModeTabSearchRegistry.getTagSearchTree(key);
                net.neoforged.neoforge.client.CreativeModeTabSearchRegistry.putTagSearchTree(key, CompletableFuture.supplyAsync(
                    () -> new IdSearchTree<>(p_344728_ -> p_344728_.getTags().map(TagKey::location), p_346121_), Util.backgroundExecutor()
                ));
                completablefuture.cancel(true);
            }
        );
    }

    public SearchTree<ItemStack> creativeTagSearch() {
        return this.creativeTagSearch(CREATIVE_TAGS);
    }

    public SearchTree<ItemStack> creativeTagSearch(SessionSearchTrees.Key key) {
        return net.neoforged.neoforge.client.CreativeModeTabSearchRegistry.getTagSearchTree(key).join();
    }

    public void updateCreativeTooltips(HolderLookup.Provider p_345391_, List<ItemStack> p_346357_) {
        this.updateCreativeTooltips(p_345391_, p_346357_, CREATIVE_NAMES);
    }

    public void updateCreativeTooltips(HolderLookup.Provider p_345391_, List<ItemStack> p_346357_, SessionSearchTrees.Key key) {
        this.register(
            key,
            () -> {
                Item.TooltipContext item$tooltipcontext = Item.TooltipContext.of(p_345391_);
                TooltipFlag tooltipflag = net.neoforged.neoforge.client.ClientTooltipFlag.of(TooltipFlag.Default.NORMAL.asCreative());
                CompletableFuture<?> completablefuture = net.neoforged.neoforge.client.CreativeModeTabSearchRegistry.getNameSearchTree(key);
                net.neoforged.neoforge.client.CreativeModeTabSearchRegistry.putNameSearchTree(key, CompletableFuture.supplyAsync(
                    () -> new FullTextSearchTree<>(
                            p_345006_ -> getTooltipLines(Stream.of(p_345006_), item$tooltipcontext, tooltipflag),
                            p_345861_ -> p_345861_.getItemHolder().unwrapKey().map(ResourceKey::location).stream(),
                            p_346357_
                        ),
                    Util.backgroundExecutor()
                ));
                completablefuture.cancel(true);
            }
        );
    }

    public SearchTree<ItemStack> creativeNameSearch() {
        return this.creativeNameSearch(CREATIVE_NAMES);
    }

    public SearchTree<ItemStack> creativeNameSearch(SessionSearchTrees.Key key) {
        return net.neoforged.neoforge.client.CreativeModeTabSearchRegistry.getNameSearchTree(key).join();
    }

    @OnlyIn(Dist.CLIENT)
    public static class Key {
    }
}
