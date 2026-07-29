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
            // 26.x: TagParser.parseTag is gone; parseCompoundFully replaces it.
            return LegacyTagToComponents.toComponentString(TagParser.parseCompoundFully(snbt));
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
     * 26.x-specific: {@code custom_model_data} is a four-list record here rather
     * than the bare int of 1.21.1, so the legacy value has to ride in the
     * {@code floats} slot — the same slot {@code applyTo} puts it in.
     */
    @Test
    void customModelDataUsesTheFloatsSlot() {
        assertEquals("[minecraft:custom_model_data={floats:[7.0f]}]", render("{CustomModelData:7}"));
    }

    /**
     * 26.x-specific: {@code custom_name}/{@code lore} are backed by
     * {@code ComponentSerialization.CODEC}, which reads the value structurally.
     * On 1.21.1 the same components used {@code FLAT_CODEC} and took a JSON
     * <em>string</em>, so legacy display text only needed re-quoting; handing
     * that string to 26.x would produce an item literally named
     * {@code {"text":"Magic Bean"}}. The JSON is transcoded to SNBT instead.
     */
    @Test
    void displayNameBecomesAnSnbtCompoundRatherThanAQuotedJsonString() {
        String named = render("{display:{Name:'{\"text\":\"Magic Bean\",\"color\":\"aqua\"}'}}");
        assertTrue(named.contains("minecraft:custom_name={"), named);
        assertTrue(named.contains("text:\"Magic Bean\""), named);
        assertTrue(named.contains("color:\"aqua\""), named);
        assertFalse(named.contains("custom_name='"),
            "the 1.21.1 quoted-JSON form would decode to a literal brace-y name on 26.x: " + named);

        // Non-JSON legacy names become a {text:…} literal, not a bare string —
        // lore is an NBT list and NBT lists are homogeneous.
        assertTrue(render("{display:{Name:\"Magic Bean\"}}")
            .contains("minecraft:custom_name={text:\"Magic Bean\"}"));

        String lore = render("{display:{Lore:['{\"text\":\"one\"}','two']}}");
        assertTrue(lore.contains("minecraft:lore=[{"), lore);
        assertTrue(lore.contains("text:\"one\""), lore);
        assertTrue(lore.contains("text:\"two\""), lore);
    }

    /**
     * 26.x-specific: {@code ItemEnchantments.CODEC} is a plain unbounded map, so
     * the {@code {levels:…,show_in_tooltip:…}} record of 1.21.1 is gone and the
     * HideFlags tooltip bit has to be expressed on {@code tooltip_display}.
     */
    @Test
    void enchantmentsAreABareMapAndHidingMovesToTooltipDisplay() {
        assertEquals("[minecraft:enchantments={\"minecraft:unbreaking\":1}]",
            render("{Enchantments:[{id:\"minecraft:unbreaking\",lvl:1}]}"));

        String hidden = render("{Enchantments:[{id:\"minecraft:unbreaking\",lvl:1}],HideFlags:1}");
        assertTrue(hidden.contains("minecraft:tooltip_display={hidden_components:[\"minecraft:enchantments\"]}"),
            hidden);

        // Nothing to hide → no tooltip_display at all, or the parser would be
        // told to hide a component that was never emitted.
        assertFalse(render("{HideFlags:1,SomePackKey:42}").contains("tooltip_display"));
    }
}
