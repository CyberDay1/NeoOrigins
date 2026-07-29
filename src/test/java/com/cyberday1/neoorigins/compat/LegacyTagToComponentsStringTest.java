package com.cyberday1.neoorigins.compat;

import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LegacyTagToComponents} has two entry points into one mapping: it can
 * mutate a live ItemStack, or render the same mapping as command-argument text
 * for {@link LegacyCommandRewriter}. They must not drift, so both are driven off
 * the same {@code RECOGNISED_KEYS} set and that contract is asserted here.
 *
 * <p>Everything below is registry-free by design — the string path runs at
 * pack-read time, before any {@code RegistryAccess} exists.
 */
class LegacyTagToComponentsStringTest {

    private static String render(String snbt) {
        try {
            return LegacyTagToComponents.toComponentString(TagParser.parseTag(snbt));
        } catch (Exception e) {
            throw new AssertionError("test fixture is not valid SNBT: " + snbt, e);
        }
    }

    /**
     * Every recognised key must map to a real component in the string path too,
     * and nothing else may: a key that one path routes and the other dumps into
     * custom_data is exactly the drift this asserts against.
     */
    @Test
    void bothEntryPointsCoverTheSameKeys() throws Exception {
        var samples = java.util.Map.of(
            "Potion",             "{Potion:\"minecraft:strength\"}",
            "CustomModelData",    "{CustomModelData:7}",
            "Damage",             "{Damage:3}",
            "Unbreakable",        "{Unbreakable:1b}",
            "RepairCost",         "{RepairCost:2}",
            "Enchantments",       "{Enchantments:[{id:\"minecraft:unbreaking\",lvl:1}]}",
            "ench",               "{ench:[{id:\"minecraft:unbreaking\",lvl:1}]}",
            "StoredEnchantments", "{StoredEnchantments:[{id:\"minecraft:unbreaking\",lvl:1}]}",
            "display",            "{display:{Name:'{\"text\":\"Hi\"}'}}");

        assertEquals(LegacyTagToComponents.recognisedKeys(), samples.keySet(),
            "every recognised key needs a sample here — add one when the set grows");

        samples.forEach((key, snbt) -> {
            String rendered = render(snbt);
            assertFalse(rendered.isEmpty(), key + " must render to something");
            assertFalse(rendered.contains("minecraft:custom_data"),
                key + " is recognised by applyTo, so the string path must map it too, not dump it: " + rendered);
        });

        // …and conversely, unrecognised keys fold into custom_data on both paths
        // rather than vanishing, so origins:nbt conditions still see them.
        String rendered = render("{HideFlags:1,SomePackKey:42,Tags:[\"a\"]}");
        assertTrue(rendered.contains("minecraft:custom_data="), rendered);
        assertTrue(rendered.contains("SomePackKey:42"), rendered);
        assertTrue(rendered.contains("HideFlags:1"), rendered);
        assertEquals("", render("{}"),
            "an empty tag renders to nothing at all, not to an empty bracket pair");
    }

    /**
     * custom_name/lore are backed by ComponentSerialization.FLAT_CODEC, which
     * reads a <em>string</em> and JSON-parses its contents — so legacy
     * display.Name (already JSON text) rides through verbatim, only re-quoted.
     */
    @Test
    void displayNameStaysJsonTextRatherThanBecomingACompound() {
        assertTrue(render("{display:{Name:'{\"text\":\"Magic Bean\",\"color\":\"aqua\"}'}}")
            .contains("minecraft:custom_name='{\"text\":\"Magic Bean\",\"color\":\"aqua\"}'"));
        assertTrue(render("{display:{Name:\"Magic Bean\"}}")
            .contains("minecraft:custom_name='\"Magic Bean\"'"),
            "non-JSON legacy names are promoted to a JSON string literal");
    }
}
