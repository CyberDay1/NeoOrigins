package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.event.MovementPowerEvents;
import com.cyberday1.neoorigins.power.registry.PowerTypes;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.service.EventPowerIndex;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code origins:carnivore} and {@code origins:vegetarian} read one tag, {@code #neoorigins:meat_foods},
 * inverted — so putting fish in it lets carnivores eat fish AND stops vegetarians eating it. Tags are
 * built from the shipped JSON (plus every classpath copy of the tags it references) by vanilla's own
 * {@link TagLoader}, so nesting and {@code required: false} behave as they do at datapack load.
 */
// hub: neoorigins/diet-tags.md
class MeatDietFishTest {

    /** A modded id resolves to this item, as if the mod were installed. Nothing else tags it. */
    private static final Item ALEXS_CATFISH = Items.NAUTILUS_SHELL;
    /** Stands in for a mod that only registers its fish to {@code c:foods/raw_fish}. */
    private static final Item CONVENTION_ONLY_FISH = Items.DRIED_KELP;

    /** The fish named directly in fish_foods, each on its own untagged stand-in. */
    private static final Map<ResourceLocation, Item> NAMED_MOD_FISH = Map.of(
        rl("alexsmobs:raw_catfish"), ALEXS_CATFISH,
        rl("alexsmobs:cooked_catfish"), Items.HEART_OF_THE_SEA,
        rl("alexsmobs:blobfish"), Items.FEATHER,
        rl("alexsmobs:flying_fish"), Items.FLINT,
        rl("alexsmobs:cosmic_cod"), Items.CLAY_BALL,
        rl("oceansdelight:fugu_slice"), Items.BRICK);

    private static final Map<ResourceLocation, Item> INSTALLED_MOD_ITEMS = installed();

    private static Map<ResourceLocation, Item> installed() {
        Map<ResourceLocation, Item> out = new HashMap<>(NAMED_MOD_FISH);
        out.put(rl("naturalist:bass"), CONVENTION_ONLY_FISH);
        return Map.copyOf(out);
    }

    private final List<PowerHolder<?>> granted = new ArrayList<>();
    private final List<ServerPlayer> players = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (int i = 0; i < granted.size(); i++) PlayerLifecycle.revoke(granted.get(i), players.get(i));
        players.forEach(sp -> EventPowerIndex.clearAll(sp.getUUID()));
        BuiltInRegistries.ITEM.resetTags();
    }

    // ---- carnivore ------------------------------------------------------

    @Test
    void carnivoreCanEatRawCod() {
        bindShippedTags(Map.of());
        assertFalse(eatIsVetoed("origins:carnivore", new ItemStack(Items.COD)),
            "cod is fish, and fish is in the carnivore diet");
    }

    @Test
    void carnivoreCanEatCookedSalmon() {
        bindShippedTags(Map.of());
        assertFalse(eatIsVetoed("origins:carnivore", new ItemStack(Items.COOKED_SALMON)),
            "cooked salmon is fish, and fish is in the carnivore diet");
    }

    /** Control: without it, "not vetoed" above could mean the handler never ran. */
    @Test
    void carnivoreStillCannotEatBread() {
        bindShippedTags(Map.of());
        assertTrue(eatIsVetoed("origins:carnivore", new ItemStack(Items.BREAD)),
            "bread is not meat or fish; the carnivore veto must still fire");
    }

    @Test
    void carnivoreCanEatAlexsMobsCatfish() {
        bindShippedTags(Map.of());
        assertFalse(eatIsVetoed("origins:carnivore", edible(ALEXS_CATFISH)),
            "Alex's Mobs files its fish under legacy tag paths; meat_foods must name them");
    }

    @Test
    void carnivoreCanEatFishFromTheConventionalTag() {
        bindShippedTags(Map.of(rl("c:foods/raw_fish"), List.of("naturalist:bass")));
        assertFalse(eatIsVetoed("origins:carnivore", edible(CONVENTION_ONLY_FISH)),
            "a mod fish in c:foods/raw_fish must reach meat_foods through fish_foods");
    }

    // ---- vegetarian: the consequence the ruling accepted ----------------

    @Test
    void vegetarianCannotEatRawCod() {
        bindShippedTags(Map.of());
        assertTrue(eatIsVetoed("origins:vegetarian", new ItemStack(Items.COD)),
            "vegetarian reads meat_foods inverted, so fish in the tag is fish it cannot eat");
    }

    @Test
    void vegetarianCannotEatCookedSalmon() {
        bindShippedTags(Map.of());
        assertTrue(eatIsVetoed("origins:vegetarian", new ItemStack(Items.COOKED_SALMON)),
            "vegetarian reads meat_foods inverted, so fish in the tag is fish it cannot eat");
    }

    @Test
    void vegetarianCannotEatAlexsMobsCatfish() {
        bindShippedTags(Map.of());
        assertTrue(eatIsVetoed("origins:vegetarian", edible(ALEXS_CATFISH)),
            "a modded fish the carnivore may eat is one the vegetarian may not");
    }

    /** Control: the vetoes above come from the tag, not from a handler that vetoes everything. */
    @Test
    void vegetarianCanStillEatBread() {
        bindShippedTags(Map.of());
        assertFalse(eatIsVetoed("origins:vegetarian", new ItemStack(Items.BREAD)),
            "bread is not in meat_foods; the vegetarian must still eat it");
    }

    // ---- ocean diet: the modded fish are fish ------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"alexsmobs:raw_catfish", "alexsmobs:cooked_catfish", "alexsmobs:blobfish",
        "alexsmobs:flying_fish", "alexsmobs:cosmic_cod", "oceansdelight:fugu_slice"})
    void oceanDietCanEatEachNamedModFish(String id) {
        bindShippedTags(Map.of());
        assertFalse(eatIsVetoed(oceanDiet(), edible(NAMED_MOD_FISH.get(rl(id)))),
            id + " is a fish, so the fish-only diet must allow it");
    }

    @ParameterizedTest
    @ValueSource(strings = {"alexsmobs:raw_catfish", "alexsmobs:cooked_catfish", "alexsmobs:blobfish",
        "alexsmobs:flying_fish", "alexsmobs:cosmic_cod", "oceansdelight:fugu_slice"})
    void carnivoreStillEatsEachNamedModFishThroughTheNest(String id) {
        bindShippedTags(Map.of());
        assertFalse(eatIsVetoed(wellKnown("origins:carnivore"), edible(NAMED_MOD_FISH.get(rl(id)))),
            id + " left meat_foods for fish_foods, which meat_foods nests");
    }

    @Test
    void oceanDietCanEatRawCod() {
        bindShippedTags(Map.of());
        assertFalse(eatIsVetoed(oceanDiet(), new ItemStack(Items.COD)));
    }

    /** Control: the passes above come from the tag, not from a handler that never vetoes. */
    @Test
    void oceanDietStillCannotEatBeef() {
        bindShippedTags(Map.of());
        assertTrue(eatIsVetoed(oceanDiet(), new ItemStack(Items.BEEF)),
            "beef is not fish; the ocean diet veto must still fire");
    }

    // ---- the pescivore tag and the nesting itself ------------------------

    @Test
    void fishTagGainedNoMeat() {
        Map<ResourceLocation, Collection<Holder<Item>>> tags = buildShippedTags(Map.of());
        Collection<Holder<Item>> fish = tags.get(rl("neoorigins:fish_foods"));
        assertNotNull(fish, "fish_foods failed to build");
        assertTrue(contains(fish, Items.COD));
        assertFalse(contains(fish, Items.BEEF), "the ocean diet must not have absorbed meat");
        NAMED_MOD_FISH.forEach((id, item) -> assertTrue(contains(fish, item), id + " must be in fish_foods"));
    }

    /** A pack that breaks fish_foods must cost the carnivore its fish, not its whole diet. */
    @Test
    void aBrokenFishTagLeavesMeatStanding() {
        Map<ResourceLocation, List<TagLoader.EntryWithSource>> files = shippedTagFiles(Map.of());
        files.put(rl("neoorigins:fish_foods"), new ArrayList<>(List.of(new TagLoader.EntryWithSource(
            TagEntry.element(rl("missingmod:no_such_fish")), "test_pack"))));
        Map<ResourceLocation, Collection<Holder<Item>>> tags = loader().build(files);
        assertFalse(tags.containsKey(rl("neoorigins:fish_foods")), "the sabotage must actually fail the tag");
        Collection<Holder<Item>> meat = tags.get(rl("neoorigins:meat_foods"));
        assertNotNull(meat, "meat_foods must not depend on fish_foods loading");
        assertTrue(contains(meat, Items.BEEF));
    }

    // ---- harness -----------------------------------------------------------

    private boolean eatIsVetoed(String wellKnownId, ItemStack food) {
        return eatIsVetoed(wellKnown(wellKnownId), food);
    }

    private boolean eatIsVetoed(PowerHolder<?> holder, ItemStack food) {
        ServerPlayer sp = PlayerLifecycle.player();
        PlayerLifecycle.grant(holder, sp);
        granted.add(holder);
        players.add(sp);
        var event = new LivingEntityUseItemEvent.Start(sp, food, 32);
        MovementPowerEvents.onItemUseStart(event);
        return event.isCanceled();
    }

    /** The same parse {@code injectWellKnownPowers} does for a pack that names the id. */
    private static PowerHolder<?> wellKnown(String id) {
        JsonObject json = OriginsCompatPowerLoader.WELL_KNOWN.get(id).get();
        PowerType<?> type = PowerTypes.get(ResourceLocation.parse(json.get("type").getAsString()));
        assertNotNull(type, id + " maps to an unregistered type");
        return holder(rl(id), type, json);
    }

    /**
     * The shipped {@code aquatic_fish_diet} with its two config reads set to their defaults, since
     * the rig loads no config: {@code fish_diet_required} on, {@code extra_fish_foods} empty.
     */
    private static PowerHolder<?> oceanDiet() {
        String path = "data/neoorigins/origins/powers/aquatic_fish_diet.json";
        JsonObject json = read(MeatDietFishTest.class.getClassLoader().getResource(path)).getAsJsonObject();
        assertEquals("ocean_origins.fish_diet_required", json.getAsJsonObject("condition").get("key").getAsString());
        json.remove("condition");
        JsonArray anyOf = json.getAsJsonObject("entity_action").getAsJsonObject("condition").getAsJsonArray("conditions");
        assertEquals("ocean_origins.extra_fish_foods", anyOf.get(1).getAsJsonObject().get("key").getAsString());
        JsonObject never = new JsonObject();
        never.addProperty("type", "neoorigins:constant");
        never.addProperty("value", false);
        anyOf.set(1, never);
        PowerType<?> type = PowerTypes.get(ResourceLocation.parse(json.get("type").getAsString()));
        return holder(rl("neoorigins:aquatic_fish_diet"), type, json);
    }

    private static <C extends PowerConfiguration> PowerHolder<C> holder(ResourceLocation id, PowerType<C> type, JsonObject json) {
        JsonObject cfg = json.deepCopy();
        cfg.addProperty("_power_id", id.toString());
        C config = type.codec().parse(JsonOps.INSTANCE, cfg).getOrThrow();
        return new PowerHolder<>(id, type, config, Component.empty(), Component.empty());
    }

    private static ItemStack edible(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.FOOD, new FoodProperties.Builder().nutrition(2).build());
        return stack;
    }

    private static void bindShippedTags(Map<ResourceLocation, List<String>> extraPackTags) {
        Map<TagKey<Item>, List<Holder<Item>>> bound = new HashMap<>();
        buildShippedTags(extraPackTags).forEach((id, holders) ->
            bound.put(TagKey.create(Registries.ITEM, id), List.copyOf(holders)));
        BuiltInRegistries.ITEM.bindTags(bound);
    }

    private static Map<ResourceLocation, Collection<Holder<Item>>> buildShippedTags(
            Map<ResourceLocation, List<String>> extraPackTags) {
        return loader().build(shippedTagFiles(extraPackTags));
    }

    private static TagLoader<Holder<Item>> loader() {
        return new TagLoader<>(id -> {
            Item standIn = INSTALLED_MOD_ITEMS.get(id);
            if (standIn != null) return Optional.of(standIn.builtInRegistryHolder());
            return BuiltInRegistries.ITEM.getHolder(ResourceKey.create(Registries.ITEM, id));
        }, "tags/item");
    }

    /**
     * Every classpath copy of {@code meat_foods}, {@code fish_foods} and each tag they reach, merged
     * the way {@code TagLoader#load} merges pack copies, then the test's own "pack" additions.
     */
    private static Map<ResourceLocation, List<TagLoader.EntryWithSource>> shippedTagFiles(
            Map<ResourceLocation, List<String>> extraPackTags) {
        Map<ResourceLocation, List<TagLoader.EntryWithSource>> out = new HashMap<>();
        Deque<ResourceLocation> todo = new ArrayDeque<>(List.of(rl("neoorigins:meat_foods"), rl("neoorigins:fish_foods")));
        Set<ResourceLocation> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            ResourceLocation tag = todo.pop();
            if (!seen.add(tag)) continue;
            List<URL> copies = copiesOf(tag);
            List<String> extra = extraPackTags.getOrDefault(tag, List.of());
            if (copies.isEmpty() && extra.isEmpty()) continue; // absent tag, as in game
            List<TagLoader.EntryWithSource> entries = new ArrayList<>();
            for (URL url : copies) {
                TagFile file = TagFile.CODEC.parse(new Dynamic<>(JsonOps.INSTANCE, read(url))).getOrThrow();
                if (file.replace()) entries.clear();
                file.entries().forEach(e -> entries.add(new TagLoader.EntryWithSource(e, url.toString())));
            }
            extra.forEach(id -> entries.add(new TagLoader.EntryWithSource(TagEntry.element(rl(id)), "test_pack")));
            out.put(tag, entries);
            for (TagLoader.EntryWithSource e : entries) {
                e.entry().visitRequiredDependencies(todo::add);
                e.entry().visitOptionalDependencies(todo::add);
            }
        }
        return out;
    }

    private static List<URL> copiesOf(ResourceLocation tag) {
        String path = "data/" + tag.getNamespace() + "/tags/item/" + tag.getPath() + ".json";
        try {
            return java.util.Collections.list(MeatDietFishTest.class.getClassLoader().getResources(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonElement read(URL url) {
        try (Reader r = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean contains(Collection<Holder<Item>> holders, Item item) {
        return holders.stream().anyMatch(h -> h.value() == item);
    }

    private static ResourceLocation rl(String id) {
        return ResourceLocation.parse(id);
    }
}
