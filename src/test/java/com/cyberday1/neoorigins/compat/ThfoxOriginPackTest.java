package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.compat.modifier.FloatModifier;
import com.cyberday1.neoorigins.power.builtin.ActionOnEventPower;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code datapacks/thfox-origin} loaded through the real managers on this line: the
 * pack's format is one this game accepts, every power and the origin load, and every
 * vanilla id it names exists in this version's registries.
 */
// hub: neoorigins/thfox-26x-port.md
class ThfoxOriginPackTest {

    private static final Path PACK = RealDatapack.projectPath("datapacks/thfox-origin");
    private static final Path DATA = PACK.resolve("data/thfox");

    @BeforeAll
    static void load() {
        RealDatapack.loadWith(PACK);
    }

    @AfterAll
    static void restore() {
        RealDatapack.restoreShipped();
    }

    @Test
    void packFormatIsOneThisGameLoads() throws IOException {
        JsonElement pack = JsonParser.parseString(Files.readString(PACK.resolve("pack.mcmeta")))
            .getAsJsonObject().get("pack");
        var meta = PackMetadataSection.SERVER_TYPE.codec().parse(JsonOps.INSTANCE, pack).getOrThrow();
        var current = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
        assertTrue(meta.supportedFormats().isValueInRange(current),
            () -> "pack declares " + meta.supportedFormats() + ", this game is " + current);
    }

    @Test
    void everyPowerFileLoads() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Path file : jsonUnder(DATA.resolve("origins/powers"))) {
            Identifier id = Identifier.fromNamespaceAndPath("thfox", stem(DATA.resolve("origins/powers"), file));
            if (PowerDataManager.INSTANCE.getPower(id) == null) missing.add(id.toString());
        }
        assertEquals(List.of(), missing, "power files that did not produce a power");
    }

    @Test
    void theFoxOriginLoadsWithEveryPowerItLists() {
        Origin fox = OriginDataManager.INSTANCE.getOrigin(Identifier.fromNamespaceAndPath("thfox", "fox"));
        assertNotNull(fox, "thfox:fox did not load");
        assertEquals(105, fox.powers().size());
        List<Identifier> dangling = fox.powers().stream()
            .filter(id -> PowerDataManager.INSTANCE.getPower(id) == null).toList();
        assertEquals(List.of(), dangling, "powers the origin lists but nothing loaded");
    }

    @Test
    void theLayerOffersTheFox() throws IOException {
        JsonObject layer = read(DATA.resolve("origins/origin_layers/origin.json"));
        String origin = layer.getAsJsonArray("origins").get(0).getAsJsonObject().get("origin").getAsString();
        assertNotNull(OriginDataManager.INSTANCE.getOrigin(Identifier.parse(origin)), origin);
    }

    @Test
    void damageTypesDecode() throws IOException {
        for (Path file : jsonUnder(DATA.resolve("damage_type"))) {
            DamageType.DIRECT_CODEC.parse(JsonOps.INSTANCE, read(file)).getOrThrow();
        }
    }

    /** A renamed or removed vanilla id loads without complaint and then silently matches nothing. */
    @Test
    void everyVanillaIdExistsInThisVersion() throws IOException {
        Map<String, Registry<?>> byField = Map.of(
            "effect", BuiltInRegistries.MOB_EFFECT,
            "sound", BuiltInRegistries.SOUND_EVENT,
            "item", BuiltInRegistries.ITEM,
            "icon", BuiltInRegistries.ITEM,
            "block", BuiltInRegistries.BLOCK,
            "entity_type", BuiltInRegistries.ENTITY_TYPE,
            "attribute", BuiltInRegistries.ATTRIBUTE);
        List<String> unknown = new ArrayList<>();
        for (Path file : jsonUnder(DATA.resolve("origins"))) {
            collect(read(file), byField, file.getFileName().toString(), unknown);
        }
        checkTag(DATA.resolve("tags/item"), BuiltInRegistries.ITEM, unknown);
        checkTag(DATA.resolve("tags/entity_type"), BuiltInRegistries.ENTITY_TYPE, unknown);
        assertEquals(List.of(), unknown);
    }

    @Test
    void foodModifiersScaleByWhatTheyDeclare() {
        assertEquals(2f, modifier("food_disliked_penalty").apply(4f), 1e-6f, "disliked food must give half nutrition");
        assertEquals(6f, modifier("hunger_exhaustion").apply(4f), 1e-6f, "a well-fed fox must tire 1.5x as fast");
    }

    @Test
    void wakingUpGivesOneItem() {
        var config = (ActionOnEventPower.Config) power("foxiality_wakeup_loot").config();
        // 26.x binds item components on a datapack reload; any of the seven loot items can roll.
        for (String item : List.of("emerald", "rabbit_foot", "rabbit_hide", "egg", "wheat", "leather", "feather")) {
            var holder = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(item)).builtInRegistryHolder();
            if (!holder.areComponentsBound()) holder.bindComponents(DataComponentMap.EMPTY);
        }
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        config.action().execute(sp);
        int items = sp.getInventory().getNonEquipmentItems().stream().mapToInt(ItemStack::getCount).sum();
        assertEquals(1, items, "waking up gave no loot");
    }

    /** An in_tag condition tests the player's biome; a tag nothing defines matches nowhere. */
    @Test
    void everyBiomeTagIsDefined() throws IOException {
        List<String> undefined = new ArrayList<>();
        for (Path file : jsonUnder(DATA.resolve("origins"))) {
            collectInTags(read(file), file.getFileName().toString(), undefined);
        }
        assertEquals(List.of(), undefined, "biome tags no loaded data defines");
    }

    private static void collectInTags(JsonElement el, String file, List<String> undefined) {
        if (el.isJsonArray()) {
            el.getAsJsonArray().forEach(e -> collectInTags(e, file, undefined));
        } else if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("type") && obj.get("type").getAsString().endsWith(":in_tag") && obj.has("tag")) {
                Identifier tag = Identifier.parse(obj.get("tag").getAsString().replace("#", ""));
                String path = "data/" + tag.getNamespace() + "/tags/worldgen/biome/" + tag.getPath() + ".json";
                if (ThfoxOriginPackTest.class.getClassLoader().getResource(path) == null
                        && !Files.exists(PACK.resolve(path))) {
                    undefined.add(file + ": " + tag);
                }
            }
            obj.entrySet().forEach(e -> collectInTags(e.getValue(), file, undefined));
        }
    }

    private static PowerHolder<?> power(String path) {
        var holder = PowerDataManager.INSTANCE.getPower(Identifier.fromNamespaceAndPath("thfox", path));
        assertNotNull(holder, "thfox:" + path + " did not load");
        return holder;
    }

    private static FloatModifier modifier(String path) {
        return ((ActionOnEventPower.Config) power(path).config()).modifier();
    }

    private static void collect(JsonElement el, Map<String, Registry<?>> byField, String file, List<String> unknown) {
        if (el.isJsonArray()) {
            el.getAsJsonArray().forEach(e -> collect(e, byField, file, unknown));
        } else if (el.isJsonObject()) {
            for (var e : el.getAsJsonObject().entrySet()) {
                Registry<?> reg = byField.get(e.getKey());
                if (reg != null && e.getValue().isJsonPrimitive()) {
                    check(e.getValue().getAsString(), reg, file, unknown);
                } else {
                    collect(e.getValue(), byField, file, unknown);
                }
            }
        }
    }

    private static void checkTag(Path dir, Registry<?> reg, List<String> unknown) throws IOException {
        for (Path file : jsonUnder(dir)) {
            for (JsonElement v : read(file).getAsJsonArray("values")) {
                check(v.getAsString(), reg, file.getFileName().toString(), unknown);
            }
        }
    }

    private static void check(String raw, Registry<?> reg, String file, List<String> unknown) {
        if (raw.startsWith("#")) return;
        if (!reg.containsKey(Identifier.parse(raw))) unknown.add(file + ": " + raw);
    }

    private static List<Path> jsonUnder(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static String stem(Path root, Path file) {
        String rel = root.relativize(file).toString().replace(java.io.File.separatorChar, '/');
        return rel.substring(0, rel.length() - ".json".length());
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }
}
