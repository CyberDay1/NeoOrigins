package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.compat.OriginsPowerTranslator;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Origins Classes' {@code more_planks_from_logs} translated to a bare
 * {@code craft_amount_bonus}, whose default output is oak planks, so every other
 * wood paid nothing. Parsed through the real codec, not read off the JSON.
 */
class MorePlanksFromLogsTest {

    private static final List<Item> PLANKS = List.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS,
        Items.BIRCH_PLANKS, Items.CHERRY_PLANKS, Items.CRIMSON_PLANKS);
    /** Stand-in for a modded plank: any item a mod's datapack puts in the tag. */
    private static final Item MODDED_PLANK = Items.STICK;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // 26.x binds item components on a datapack reload; the test stacks need them.
        List<Item> items = new ArrayList<>(PLANKS);
        items.add(MODDED_PLANK);
        items.add(Items.CRAFTING_TABLE);
        for (Item item : items) {
            if (!item.builtInRegistryHolder().areComponentsBound()) {
                item.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
            }
        }
    }

    @BeforeEach
    void bindPlanks() {
        List<Holder<Item>> members = new ArrayList<>();
        for (Item item : PLANKS) members.add(item.builtInRegistryHolder());
        members.add(MODDED_PLANK.builtInRegistryHolder());
        applyTags(Map.of(ItemTags.PLANKS, members));
    }

    @AfterEach
    void unbind() {
        applyTags(Map.of());
    }

    @Test
    void everyWoodMatches() {
        String output = translatedOutputItem();
        for (Item plank : PLANKS) {
            assertTrue(CraftAmountBonusPower.matches(new ItemStack(plank), output),
                BuiltInRegistries.ITEM.getKey(plank) + " got no bonus from " + output);
        }
        assertTrue(CraftAmountBonusPower.matches(new ItemStack(MODDED_PLANK), output));
    }

    @Test
    void aNonPlankDoesNotMatch() {
        assertFalse(CraftAmountBonusPower.matches(new ItemStack(Items.CRAFTING_TABLE), translatedOutputItem()));
    }

    private static void applyTags(Map<TagKey<Item>, List<Holder<Item>>> tags) {
        BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, tags)).apply();
    }

    private static String translatedOutputItem() {
        JsonObject out = OriginsPowerTranslator.translate(
            Identifier.parse("origins-classes:more_planks_from_logs"),
            JsonParser.parseString("{\"type\":\"origins:simple\"}").getAsJsonObject()).orElseThrow();
        return CraftAmountBonusPower.Config.CODEC.parse(JsonOps.INSTANCE, out).getOrThrow().outputItem();
    }
}
