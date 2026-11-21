package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HasIngredientConditionTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // The harness does not bind item components; ItemStack needs them.
        for (Item item : List.of(Items.CHEST, Items.CRAFTING_TABLE, Items.CHARCOAL)) {
            if (!item.builtInRegistryHolder().areComponentsBound()) {
                item.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
            }
        }
    }

    @AfterEach
    void reset() {
        HasIngredientCondition.setSourceForTest(null);
        applyItemTags(Map.of());
    }

    private static JsonObject json(String s) { return JsonParser.parseString(s).getAsJsonObject(); }

    private static void applyItemTags(Map<TagKey<Item>, List<Holder<Item>>> tags) {
        BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, tags)).apply();
    }

    private static boolean test(String body, ServerPlayer player) {
        return ConditionParser.parse(json("{\"type\":\"neoorigins:has_ingredient\"," + body + "}"), "has-ingredient-test")
            .test(player);
    }

    /** chest <- oak/birch planks <- oak log; a crafting table recipe that loops back to itself. */
    private static void fakeRecipes() {
        HasIngredientCondition.setSourceForTest(types -> types.contains(HasIngredientCondition.CRAFTING)
            ? Map.of(
                Items.CHEST, Set.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS),
                Items.OAK_PLANKS, Set.of(Items.OAK_LOG),
                Items.CRAFTING_TABLE, Set.of(Items.CRAFTING_TABLE, Items.OAK_PLANKS))
            : Map.of(Items.CHARCOAL, Set.of(Items.OAK_LOG)));
    }

    // ---- graph ----------------------------------------------------------

    @Test
    void graphFollowsDepthAndSurvivesCycles() {
        var graph = new HasIngredientCondition.Graph<>(Map.of(
            "a", Set.of("b"), "b", Set.of("c", "a"), "c", Set.of("d")));
        assertEquals(Set.of("b"), graph.reachable("a", 1));
        assertEquals(Set.of("b", "c", "a"), graph.reachable("a", 2));
        assertEquals(Set.of("b", "c", "a", "d"), graph.reachable("a", 8));
        assertEquals(Set.of(), graph.reachable("d", 8));
        assertSame(graph.reachable("a", 2), graph.reachable("a", 2), "closures are memoized");
    }

    // ---- parsing --------------------------------------------------------

    @Test
    void defaultsAndClamping() {
        var spec = HasIngredientCondition.parseSpec(json("{\"item\":\"minecraft:diamond\"}"), "t");
        assertNotNull(spec);
        assertEquals(Items.DIAMOND, spec.item());
        assertNull(spec.tag());
        assertEquals(HasIngredientCondition.Slot.HELD, spec.slot());
        assertEquals(1, spec.depth());
        assertEquals(Set.of(HasIngredientCondition.CRAFTING), spec.recipeTypes());

        assertEquals(1, HasIngredientCondition.parseSpec(json("{\"item\":\"minecraft:diamond\",\"depth\":0}"), "t").depth());
        assertEquals(8, HasIngredientCondition.parseSpec(json("{\"item\":\"minecraft:diamond\",\"depth\":99}"), "t").depth());
        assertEquals(HasIngredientCondition.Slot.HELD,
            HasIngredientCondition.parseSpec(json("{\"item\":\"minecraft:diamond\",\"slot\":\"pocket\"}"), "t").slot());
        assertEquals(HasIngredientCondition.Slot.INVENTORY,
            HasIngredientCondition.parseSpec(json("{\"item\":\"minecraft:diamond\",\"slot\":\"Inventory\"}"), "t").slot());
        var tagged = HasIngredientCondition.parseSpec(json("{\"tag\":\"#minecraft:planks\"}"), "t");
        assertEquals(ItemTags.PLANKS, tagged.tag());
        var types = HasIngredientCondition.parseSpec(json(
            "{\"item\":\"minecraft:diamond\",\"recipe_types\":[\"minecraft:smelting\",\"nope:nothing\"]}"), "t");
        assertEquals(Set.of(Identifier.parse("minecraft:smelting")), types.recipeTypes());
    }

    @Test
    void badInputFailsClosed() {
        assertNull(HasIngredientCondition.parseSpec(json("{}"), "t"));
        assertNull(HasIngredientCondition.parseSpec(json("{\"item\":\"minecraft:diamond\",\"tag\":\"minecraft:planks\"}"), "t"));
        assertNull(HasIngredientCondition.parseSpec(json("{\"item\":\"minecraft:not_an_item\"}"), "t"));
        assertNull(HasIngredientCondition.parseSpec(json("{\"item\":\"minecraft:diamond\",\"recipe_types\":[\"nope:nothing\"]}"), "t"));

        fakeRecipes();
        var player = PlayerLifecycle.realPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHEST));
        assertFalse(test("\"item\":\"minecraft:oak_planks\",\"tag\":\"minecraft:planks\"", player));
        assertTrue(test("\"inverted\":true", player), "inverted wraps the fail-closed result like any condition");
    }

    // ---- matching -------------------------------------------------------

    @Test
    void itemTagAndDepthMatching() {
        applyItemTags(Map.of(ItemTags.PLANKS, List.of(Items.BIRCH_PLANKS.builtInRegistryHolder())));
        fakeRecipes();
        var player = PlayerLifecycle.realPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHEST));

        assertTrue(test("\"item\":\"minecraft:oak_planks\"", player));
        assertTrue(test("\"tag\":\"minecraft:planks\"", player));
        assertFalse(test("\"tag\":\"minecraft:logs\"", player));
        assertFalse(test("\"item\":\"minecraft:oak_log\"", player));
        assertTrue(test("\"item\":\"minecraft:oak_log\",\"depth\":2", player));
        assertFalse(test("\"item\":\"minecraft:oak_planks\",\"inverted\":true", player));
        assertFalse(test("\"item\":\"minecraft:oak_log\",\"recipe_types\":[\"minecraft:smelting\"]", player),
            "only the listed recipe types count");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CRAFTING_TABLE));
        assertTrue(test("\"item\":\"minecraft:oak_log\",\"depth\":8", player), "a self-referencing recipe terminates");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHARCOAL));
        assertTrue(test("\"item\":\"minecraft:oak_log\",\"recipe_types\":[\"minecraft:smelting\"]", player));
        assertFalse(test("\"item\":\"minecraft:oak_log\"", player));
    }

    @Test
    void slotRanges() {
        fakeRecipes();
        var player = PlayerLifecycle.realPlayer();
        var inv = player.getInventory();
        String planks = "\"item\":\"minecraft:oak_planks\"";

        assertFalse(test(planks, player), "empty stacks never match");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.CHEST));
        assertTrue(test(planks, player), "held covers the offhand");
        assertTrue(test(planks + ",\"slot\":\"inventory\"", player), "inventory covers the offhand");
        assertFalse(test(planks + ",\"slot\":\"hotbar\"", player));

        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        inv.getNonEquipmentItems().set(5, new ItemStack(Items.CHEST));
        inv.setSelectedSlot(0);
        assertFalse(test(planks, player), "a hotbar slot that is not selected is not held");
        assertTrue(test(planks + ",\"slot\":\"hotbar\"", player));

        inv.getNonEquipmentItems().set(5, ItemStack.EMPTY);
        inv.getNonEquipmentItems().set(20, new ItemStack(Items.CHEST));
        assertFalse(test(planks + ",\"slot\":\"hotbar\"", player));
        assertTrue(test(planks + ",\"slot\":\"inventory\"", player));

        assertEquals(37, HasIngredientCondition.stacks(player, HasIngredientCondition.Slot.INVENTORY).size());
        assertEquals(9, HasIngredientCondition.stacks(player, HasIngredientCondition.Slot.HOTBAR).size());
    }

    // ---- server lookup --------------------------------------------------

    @Test
    void readsTheServerRecipeManagerAndFailsClosedWithoutOne() {
        assertFalse(ConditionParser.parse(json("{\"type\":\"neoorigins:has_ingredient\",\"item\":\"minecraft:oak_planks\"}"), "t")
            .test(PlayerLifecycle.player()), "no server: fail closed");

        var server = Mockito.mock(MinecraftServer.class, Mockito.RETURNS_DEEP_STUBS);
        var chest = new ShapelessRecipe(new Recipe.CommonInfo(true),
            new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
            new ItemStackTemplate(Items.CHEST), List.of(Ingredient.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS)));
        var charcoal = new SmeltingRecipe(new Recipe.CommonInfo(true),
            new AbstractCookingRecipe.CookingBookInfo(CookingBookCategory.MISC, ""),
            Ingredient.of(Items.OAK_LOG), new ItemStackTemplate(Items.CHARCOAL), 0.1f, 200);
        List<RecipeHolder<?>> recipes = List.of(
            new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, Identifier.parse("test:chest")), chest),
            new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, Identifier.parse("test:charcoal")), charcoal));
        Mockito.when(server.getRecipeManager().getRecipes()).thenReturn(recipes);
        HasIngredientCondition.clearCache();
        var player = PlayerLifecycle.realPlayer(server);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHEST));

        assertTrue(test("\"item\":\"minecraft:birch_planks\"", player));
        assertFalse(test("\"item\":\"minecraft:oak_log\",\"depth\":3", player));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHARCOAL));
        assertFalse(test("\"item\":\"minecraft:oak_log\"", player), "smelting is not crafting");
        assertTrue(test("\"item\":\"minecraft:oak_log\",\"recipe_types\":[\"minecraft:smelting\"]", player));
        HasIngredientCondition.clearCache();
    }
}
