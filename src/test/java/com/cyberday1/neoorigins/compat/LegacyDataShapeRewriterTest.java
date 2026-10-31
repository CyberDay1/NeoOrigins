package com.cyberday1.neoorigins.compat;

import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Legacy recipe, advancement and loot shapes served from originpacks, on the 26.3 codecs. */
class LegacyDataShapeRewriterTest {

    private static void assertJson(String expected, String actual) {
        assertEquals(JsonParser.parseString(expected), JsonParser.parseString(actual));
    }

    // ── recipes ─────────────────────────────────────────────────────────────

    @Test
    void legacyShapedRecipeGetsIdResultAndStringIngredients() {
        String legacy = """
            {"type":"minecraft:crafting_shaped","pattern":["# #","# #"],
             "key":{"#":{"item":"minecraft:gold_block"},"T":{"tag":"minecraft:planks"}},
             "result":{"item":"minecraft:golden_boots"}}""";
        assertJson("""
            {"type":"minecraft:crafting_shaped","pattern":["# #","# #"],
             "key":{"#":"minecraft:gold_block","T":"#minecraft:planks"},
             "result":{"id":"minecraft:golden_boots"}}""",
            LegacyDataShapeRewriter.rewrite("recipe/golden_boots.json", legacy));
    }

    @Test
    void resultCountAndKeyOrderSurvive() {
        String out = LegacyDataShapeRewriter.rewrite("recipes/x.json",
            "{\"type\":\"crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:stick\"}],"
                + "\"result\":{\"item\":\"minecraft:torch\",\"count\":4}}");
        assertEquals("{\"type\":\"crafting_shapeless\",\"ingredients\":[\"minecraft:stick\"],"
            + "\"result\":{\"id\":\"minecraft:torch\",\"count\":4}}", out);
    }

    @Test
    void legacyIngredientListsAndSingleFieldsConvert() {
        assertJson("""
            {"type":"minecraft:smelting","ingredient":["minecraft:sand","minecraft:red_sand"],
             "result":{"id":"minecraft:glass"}}""",
            LegacyDataShapeRewriter.rewrite("recipe/glass.json", """
            {"type":"minecraft:smelting","ingredient":[{"item":"minecraft:sand"},{"item":"minecraft:red_sand"}],
             "result":{"id":"minecraft:glass"}}"""));
        assertJson("""
            {"type":"minecraft:smithing_transform","template":"minecraft:netherite_upgrade_smithing_template",
             "base":"#minecraft:swords","addition":"minecraft:netherite_ingot","result":{"id":"minecraft:netherite_sword"}}""",
            LegacyDataShapeRewriter.rewrite("recipe/sword.json", """
            {"type":"minecraft:smithing_transform","template":{"item":"minecraft:netherite_upgrade_smithing_template"},
             "base":[{"tag":"minecraft:swords"}],"addition":{"item":"minecraft:netherite_ingot"},
             "result":{"item":"minecraft:netherite_sword"}}"""));
    }

    @Test
    void inexpressibleOrCustomIngredientsAreLeftAlone() {
        // A list mixing a tag cannot be a 26.x holder set; a custom ingredient carries a type.
        assertNull(LegacyDataShapeRewriter.rewrite("recipe/a.json", """
            {"type":"minecraft:smelting","ingredient":[{"item":"minecraft:sand"},{"tag":"minecraft:logs"}],
             "result":{"id":"minecraft:glass"}}"""));
        assertNull(LegacyDataShapeRewriter.rewrite("recipe/b.json", """
            {"type":"minecraft:crafting_shapeless",
             "ingredients":[{"type":"neoforge:components","items":"minecraft:stick","components":{}}],
             "result":{"id":"minecraft:torch"}}"""));
    }

    @Test
    void modernRecipeIsUnchanged() {
        assertNull(LegacyDataShapeRewriter.rewrite("recipe/golden_boots.json", """
            {"type":"minecraft:crafting_shaped","pattern":["#"],
             "key":{"#":"minecraft:gold_block","s":["minecraft:stick","minecraft:bone"],"t":"#minecraft:logs"},
             "result":{"id":"minecraft:golden_boots","count":1}}"""));
    }

    @Test
    void resultWithNbtOrModdedTypeIsLeftAlone() {
        assertNull(LegacyDataShapeRewriter.rewrite("recipe/a.json",
            "{\"type\":\"minecraft:smelting\",\"ingredient\":\"minecraft:sand\","
                + "\"result\":{\"item\":\"minecraft:glass\",\"nbt\":\"{a:1}\"}}"));
        assertNull(LegacyDataShapeRewriter.rewrite("recipe/b.json",
            "{\"type\":\"othermod:press\",\"result\":{\"item\":\"minecraft:glass\"}}"));
    }

    // ── advancements ────────────────────────────────────────────────────────

    @Test
    void legacyAdvancementIconGetsId() {
        String legacy = """
            {"display":{"title":"t","description":"d","icon":{"item":"minecraft:zombie_head"}},
             "criteria":{"a":{"trigger":"minecraft:impossible"}}}""";
        assertJson("""
            {"display":{"title":"t","description":"d","icon":{"id":"minecraft:zombie_head"}},
             "criteria":{"a":{"trigger":"minecraft:impossible"}}}""",
            LegacyDataShapeRewriter.rewrite("advancements/deathsworn/root.json", legacy));
    }

    @Test
    void childAdvancementLosesBackgroundButRootKeepsIt() {
        assertJson("""
            {"display":{"icon":{"id":"minecraft:bone"},"title":"t","description":"d"},
             "criteria":{"b":{"trigger":"minecraft:impossible"}},"parent":"ns:root"}""",
            LegacyDataShapeRewriter.rewrite("advancements/deathsworn/skeleton.json", """
            {"display":{"icon":{"item":"minecraft:bone"},"title":"t","description":"d",
              "background":"minecraft:gui/advancements/backgrounds/stone"},
             "criteria":{"b":{"trigger":"minecraft:impossible"}},"parent":"ns:root"}"""));
        assertNull(LegacyDataShapeRewriter.rewrite("advancement/root.json", """
            {"display":{"icon":{"id":"minecraft:bone"},"title":"t","description":"d",
              "background":"minecraft:gui/advancements/backgrounds/stone"},
             "criteria":{"b":{"trigger":"minecraft:impossible"}}}"""));
    }

    @Test
    void modernAdvancementAndCriteriaItemsAreUnchanged() {
        assertNull(LegacyDataShapeRewriter.rewrite("advancement/a.json", """
            {"display":{"title":"t","description":"d","icon":{"id":"minecraft:bone"}},
             "criteria":{"a":{"trigger":"minecraft:inventory_changed",
               "conditions":{"items":[{"item":"minecraft:bone"}]}}}}"""));
    }

    @Test
    void otherPathsAreNotHandled() {
        assertEquals(false, LegacyDataShapeRewriter.handles("powers/x.json"));
        assertEquals(false, LegacyDataShapeRewriter.handles("recipe/x.mcfunction"));
        assertEquals(true, LegacyDataShapeRewriter.handles("recipes/x.json"));
    }

    // ── loot data (26.3) ────────────────────────────────────────────────────

    @Test
    void legacyPredicateKeyBecomesType() {
        assertJson("{\"type\":\"minecraft:random_chance\",\"chance\":0.05}",
            LegacyDataShapeRewriter.rewrite("predicate/chance.json",
                "{\"condition\":\"minecraft:random_chance\",\"chance\":0.05}"));
        assertJson("""
            {"type":"minecraft:inverted","term":{"type":"minecraft:weather_check","raining":true}}""",
            LegacyDataShapeRewriter.rewrite("predicates/dry.json", """
            {"condition":"minecraft:inverted","term":{"condition":"minecraft:weather_check","raining":true}}"""));
    }

    @Test
    void legacyPredicateArrayBecomesAllOf() {
        assertJson("""
            {"type":"minecraft:all_of","terms":[{"type":"minecraft:random_chance","chance":0.5},"ns:other"]}""",
            LegacyDataShapeRewriter.rewrite("predicate/both.json", """
            [{"condition":"minecraft:random_chance","chance":0.5},"ns:other"]"""));
    }

    @Test
    void modernPredicateIsUnchanged() {
        assertNull(LegacyDataShapeRewriter.rewrite("predicate/a.json",
            "{\"type\":\"minecraft:entity_properties\",\"entity\":\"this\",\"predicate\":{\"flags\":{\"is_sneaking\":true}}}"));
        assertNull(LegacyDataShapeRewriter.rewrite("predicate/b.json",
            "{\"type\":\"minecraft:any_of\",\"terms\":[{\"type\":\"minecraft:random_chance\",\"chance\":0.5}]}"));
    }

    @Test
    void legacyItemModifierArrayAndFunctionKeyConvert() {
        assertJson("""
            {"type":"minecraft:sequence","functions":[{"type":"minecraft:set_count","count":-1,"add":true}]}""",
            LegacyDataShapeRewriter.rewrite("item_modifiers/consume.json",
                "[{\"function\":\"minecraft:set_count\",\"count\":-1,\"add\":true}]"));
        assertJson("{\"type\":\"minecraft:fill_player_head\",\"entity\":\"this\"}",
            LegacyDataShapeRewriter.rewrite("item_modifier/get_head.json",
                "{\"function\":\"minecraft:fill_player_head\",\"entity\":\"this\"}"));
    }

    @Test
    void modernItemModifierIsUnchanged() {
        assertNull(LegacyDataShapeRewriter.rewrite("item_modifier/a.json", """
            {"type":"minecraft:sequence","functions":[{"type":"minecraft:set_count","count":{"type":"minecraft:uniform","min":1,"max":2}}]}"""));
    }

    @Test
    void legacyLootTableIsRekeyedThroughout() {
        String legacy = """
            {"pools":[{"rolls":{"min":1,"max":3},
              "entries":[{"type":"minecraft:item","name":"minecraft:emerald",
                "functions":[{"function":"minecraft:set_count","count":{"min":1,"max":3}}]}],
              "conditions":[{"condition":"minecraft:killed_by_player"},
                            {"condition":"minecraft:random_chance","chance":0.5}]}]}""";
        assertJson("""
            {"pools":[{"rolls":{"type":"minecraft:uniform","min":1,"max":3},
              "entries":[{"type":"minecraft:item","name":"minecraft:emerald",
                "modifier":{"type":"minecraft:set_count","count":{"type":"minecraft:uniform","min":1,"max":3}}}],
              "condition":{"type":"minecraft:all_of","terms":[{"type":"minecraft:killed_by_player"},
                            {"type":"minecraft:random_chance","chance":0.5}]}}]}""",
            LegacyDataShapeRewriter.rewrite("loot_tables/assassin.json", legacy));
    }

    @Test
    void modernLootTableIsUnchanged() {
        // A string condition on a pool is a predicate reference, not a legacy key.
        assertNull(LegacyDataShapeRewriter.rewrite("loot_table/a.json", """
            {"pools":[{"rolls":{"type":"minecraft:uniform","min":1,"max":3},"condition":"ns:chance",
              "entries":[{"type":"minecraft:item","name":"minecraft:emerald",
                "modifier":[{"type":"minecraft:set_count","count":2}]}]}]}"""));
    }

    // ── pack-layer gate ─────────────────────────────────────────────────────

    private static final class FakePack implements PackResources {
        final Map<String, String> files = new HashMap<>();

        @Override public IoSupplier<InputStream> getRootResource(String... elements) { return null; }
        @Override public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
            String body = files.get(location.getPath());
            return body == null ? null : () -> new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }
        @Override public void listResources(PackType type, String namespace, String path, ResourceOutput output) {}
        @Override public Set<String> getNamespaces(PackType type) { return Set.of("x"); }
        @Override public <T> T getMetadataSection(MetadataSectionType<T> serializer) { return null; }
        @Override public PackLocationInfo location() {
            return new PackLocationInfo("fake", Component.literal("fake"), PackSource.WORLD, Optional.empty());
        }
        @Override public boolean isHidden() { return false; }
        @Override public void close() {}
    }

    private static String get(PackResources pack, String path) throws Exception {
        IoSupplier<InputStream> io = pack.getResource(PackType.SERVER_DATA, Identifier.fromNamespaceAndPath("x", path));
        try (InputStream in = io.get()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void onlyOriginpacksGetTheShapeRewrite() throws Exception {
        String legacy = "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[\"minecraft:stick\"],"
            + "\"result\":{\"item\":\"minecraft:torch\"}}";
        FakePack inner = new FakePack();
        // Legacy plural folder, so the lookup also goes through the folder remap.
        inner.files.put("recipes/torch.json", legacy);

        assertEquals(legacy, get(new LegacyFolderPackResources(inner), "recipe/torch.json"));
        assertJson(legacy.replace("\"item\"", "\"id\""),
            get(new LegacyFolderPackResources(inner, true), "recipe/torch.json"));
    }
}
