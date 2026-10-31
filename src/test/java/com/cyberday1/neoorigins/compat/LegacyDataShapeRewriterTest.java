package com.cyberday1.neoorigins.compat;

import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
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

/** Legacy recipe/advancement shapes served from originpacks, on the 1.21.1 codecs. */
class LegacyDataShapeRewriterTest {

    private static void assertJson(String expected, String actual) {
        assertEquals(JsonParser.parseString(expected), JsonParser.parseString(actual));
    }

    // ── recipes ─────────────────────────────────────────────────────────────

    @Test
    void legacyRecipeResultGetsIdAndIngredientsStay() {
        String legacy = """
            {"type":"minecraft:crafting_shaped","pattern":["# #","# #"],
             "key":{"#":{"item":"minecraft:gold_block"}},
             "result":{"item":"minecraft:golden_boots"}}""";
        assertJson("""
            {"type":"minecraft:crafting_shaped","pattern":["# #","# #"],
             "key":{"#":{"item":"minecraft:gold_block"}},
             "result":{"id":"minecraft:golden_boots"}}""",
            LegacyDataShapeRewriter.rewrite("recipe/golden_boots.json", legacy));
    }

    @Test
    void resultCountAndKeyOrderSurvive() {
        String out = LegacyDataShapeRewriter.rewrite("recipes/x.json",
            "{\"type\":\"crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:stick\"}],"
                + "\"result\":{\"item\":\"minecraft:torch\",\"count\":4}}");
        assertEquals("{\"type\":\"crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:stick\"}],"
            + "\"result\":{\"id\":\"minecraft:torch\",\"count\":4}}", out);
    }

    @Test
    void modernRecipeIsUnchanged() {
        assertNull(LegacyDataShapeRewriter.rewrite("recipe/golden_boots.json", """
            {"type":"minecraft:crafting_shaped","pattern":["#"],
             "key":{"#":{"item":"minecraft:gold_block"}},
             "result":{"id":"minecraft:golden_boots","count":1}}"""));
    }

    @Test
    void resultWithNbtOrModdedTypeIsLeftAlone() {
        assertNull(LegacyDataShapeRewriter.rewrite("recipe/a.json",
            "{\"type\":\"minecraft:smelting\",\"ingredient\":{\"item\":\"minecraft:sand\"},"
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

    // ── pack-layer gate ─────────────────────────────────────────────────────

    private static final class FakePack implements PackResources {
        final Map<String, String> files = new HashMap<>();

        @Override public IoSupplier<InputStream> getRootResource(String... elements) { return null; }
        @Override public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
            String body = files.get(location.getPath());
            return body == null ? null : () -> new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }
        @Override public void listResources(PackType type, String namespace, String path, ResourceOutput output) {}
        @Override public Set<String> getNamespaces(PackType type) { return Set.of("x"); }
        @Override public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) { return null; }
        @Override public PackLocationInfo location() {
            return new PackLocationInfo("fake", Component.literal("fake"), PackSource.WORLD, Optional.empty());
        }
        @Override public boolean isHidden() { return false; }
        @Override public void close() {}
    }

    private static String get(PackResources pack, String path) throws Exception {
        IoSupplier<InputStream> io = pack.getResource(PackType.SERVER_DATA, ResourceLocation.fromNamespaceAndPath("x", path));
        try (InputStream in = io.get()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void onlyOriginpacksGetTheShapeRewrite() throws Exception {
        String legacy = "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[],"
            + "\"result\":{\"item\":\"minecraft:torch\"}}";
        FakePack inner = new FakePack();
        // Legacy plural folder, so the lookup also goes through the folder remap.
        inner.files.put("recipes/torch.json", legacy);

        assertEquals(legacy, get(new LegacyFolderPackResources(inner), "recipe/torch.json"));
        assertJson(legacy.replace("\"item\"", "\"id\""),
            get(new LegacyFolderPackResources(inner, true), "recipe/torch.json"));
    }

    // ── alternative → any_of ────────────────────────────────────────────────

    @Test
    void alternativeConditionBecomesAnyOf() {
        assertJson("""
            {"condition":"minecraft:any_of","terms":[{"condition":"minecraft:random_chance","chance":0.5}]}""",
            LegacyDataShapeRewriter.rewrite("predicates/coin.json", """
                {"condition":"alternative","terms":[{"condition":"minecraft:random_chance","chance":0.5}]}"""));
        assertJson("""
            {"pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:stone",
              "conditions":[{"condition":"minecraft:any_of","terms":[]}]}]}]}""",
            LegacyDataShapeRewriter.rewrite("loot_table/x.json", """
                {"pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:stone",
                  "conditions":[{"condition":"minecraft:alternative","terms":[]}]}]}]}"""));
        assertJson("""
            {"criteria":{"a":{"trigger":"minecraft:tick","conditions":{"player":[
              {"condition":"minecraft:any_of","terms":[]}]}}}}""",
            LegacyDataShapeRewriter.rewrite("advancement/x.json", """
                {"criteria":{"a":{"trigger":"minecraft:tick","conditions":{"player":[
                  {"condition":"minecraft:alternative","terms":[]}]}}}}"""));
    }

    @Test
    void modernLootDataIsUnchanged() {
        assertNull(LegacyDataShapeRewriter.rewrite("predicate/coin.json",
            "{\"condition\":\"minecraft:any_of\",\"terms\":[]}"));
        assertNull(LegacyDataShapeRewriter.rewrite("loot_table/x.json",
            "{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:alternative\"}]}]}"));
        assertNull(LegacyDataShapeRewriter.rewrite("item_modifier/x.json",
            "{\"function\":\"minecraft:set_count\",\"count\":2}"));
    }
}
