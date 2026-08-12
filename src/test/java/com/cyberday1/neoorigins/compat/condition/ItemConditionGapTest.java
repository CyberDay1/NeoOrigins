package com.cyberday1.neoorigins.compat.condition;

import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The item conditions the compat harness had listed as unsupported. Until they
 * were implemented they fell to the parser's default arm, which returns
 * alwaysTrue — so an Origins++ armour restriction written as
 * {@code {"type":"origins:armor_value","comparison":">","compare_to":2}} matched
 * every stack and restricted nothing at all. Fail-OPEN, and silent: the only
 * trace was a debug-level counter.
 *
 * <p>These assert the resolution helpers on real stacks rather than only that a
 * parse succeeded. A parse-only test passes on a parser that recognises the verb
 * and then computes the wrong number, which is the same silent-wrong-answer bug
 * one layer down.
 */
class ItemConditionGapTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---- armor_value -----------------------------------------------------

    @Test
    void armorValueReadsTheStacksOwnArmour() {
        // Vanilla diamond chestplate: 8 armour. Read off the attribute-modifiers
        // component, which is where armour has lived since 1.20.5 — the pre-1.21
        // ArmorItem.getDefense() route Apoli used does not exist on 26.x at all.
        assertEquals(8.0, ItemConditionParser.armorValueOf(Items.DIAMOND_CHESTPLATE.getDefaultInstance()));
        assertEquals(2.0, ItemConditionParser.armorValueOf(Items.LEATHER_LEGGINGS.getDefaultInstance()));
    }

    @Test
    void armorValueIsZeroForThingsThatArentArmour() {
        assertEquals(0.0, ItemConditionParser.armorValueOf(Items.STICK.getDefaultInstance()));
        assertEquals(0.0, ItemConditionParser.armorValueOf(ItemStack.EMPTY));
    }

    @Test
    void armorValueConditionDiscriminates() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "origins:armor_value");
        json.addProperty("comparison", ">");
        json.addProperty("compare_to", 2);
        ItemCondition cond = ItemConditionParser.parse(json);

        assertTrue(cond.test(Items.DIAMOND_CHESTPLATE.getDefaultInstance()),
            "8 armour must satisfy > 2");
        assertFalse(cond.test(Items.LEATHER_LEGGINGS.getDefaultInstance()),
            "2 armour must not satisfy > 2");
        assertFalse(cond.test(Items.STICK.getDefaultInstance()),
            "a non-armour stack must not satisfy > 2 — this is the fail-open regression");
    }

    // ---- harvest_level ---------------------------------------------------

    @Test
    void harvestLevelRecoversApolisTierNumbering() {
        assertEquals(0, ItemConditionParser.harvestLevelOf(Items.WOODEN_PICKAXE.getDefaultInstance()));
        // Gold shares wood's level in Apoli despite mining faster.
        assertEquals(0, ItemConditionParser.harvestLevelOf(Items.GOLDEN_PICKAXE.getDefaultInstance()));
        assertEquals(1, ItemConditionParser.harvestLevelOf(Items.STONE_PICKAXE.getDefaultInstance()));
        assertEquals(2, ItemConditionParser.harvestLevelOf(Items.IRON_PICKAXE.getDefaultInstance()));
        assertEquals(3, ItemConditionParser.harvestLevelOf(Items.DIAMOND_PICKAXE.getDefaultInstance()));
        assertEquals(4, ItemConditionParser.harvestLevelOf(Items.NETHERITE_PICKAXE.getDefaultInstance()));
    }

    @Test
    void harvestLevelIsZeroForNonTools() {
        assertEquals(0, ItemConditionParser.harvestLevelOf(Items.STICK.getDefaultInstance()));
        assertEquals(0, ItemConditionParser.harvestLevelOf(ItemStack.EMPTY));
    }

    /**
     * Origins++ bedrockean gates bedrock-breaking on {@code >= 4}. Before this
     * the condition was alwaysTrue, so a wooden pickaxe passed.
     */
    @Test
    void harvestLevelConditionGatesOnNetheriteTier() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "origins:harvest_level");
        json.addProperty("comparison", ">=");
        json.addProperty("compare_to", 4);
        ItemCondition cond = ItemConditionParser.parse(json);

        assertTrue(cond.test(Items.NETHERITE_PICKAXE.getDefaultInstance()));
        assertFalse(cond.test(Items.DIAMOND_PICKAXE.getDefaultInstance()));
        assertFalse(cond.test(Items.WOODEN_PICKAXE.getDefaultInstance()));
    }

    // ---- durability ------------------------------------------------------

    @Test
    void durabilityIsWhatIsLeftNotWhatItStartedWith() {
        ItemStack pick = Items.IRON_PICKAXE.getDefaultInstance();
        int max = pick.getMaxDamage();
        assertEquals(max, ItemConditionParser.remainingDurabilityOf(pick));

        pick.set(DataComponents.DAMAGE, 10);
        assertEquals(max - 10, ItemConditionParser.remainingDurabilityOf(pick));
    }

    @Test
    void durabilityIsZeroForUndamageableItems() {
        assertEquals(0, ItemConditionParser.remainingDurabilityOf(Items.STONE.getDefaultInstance()));
        assertEquals(0, ItemConditionParser.remainingDurabilityOf(ItemStack.EMPTY));
    }

    // ---- meat ------------------------------------------------------------

    /**
     * Only the negative half is assertable here: {@code meat} resolves through
     * the NeoForge food tags, and tags are unbound until a server loads them, so
     * every {@code is(TagKey)} is false in a bare bootstrap. Beef returning false
     * below is therefore NOT evidence of correctness, which is exactly why this
     * test does not assert it — the positive case is covered by the headless
     * pack-boot gate instead.
     */
    @Test
    void meatRejectsNonFoodAndEmptyStacks() {
        assertFalse(ItemConditionParser.isMeat(Items.STICK.getDefaultInstance()));
        assertFalse(ItemConditionParser.isMeat(ItemStack.EMPTY));
    }

    // ---- constant --------------------------------------------------------

    @Test
    void constantHonoursItsValue() {
        JsonObject t = new JsonObject();
        t.addProperty("type", "origins:constant");
        t.addProperty("value", true);
        assertTrue(ItemConditionParser.parse(t).test(ItemStack.EMPTY));

        JsonObject f = new JsonObject();
        f.addProperty("type", "origins:constant");
        f.addProperty("value", false);
        assertFalse(ItemConditionParser.parse(f).test(ItemStack.EMPTY));
    }

    // ---- registry drift --------------------------------------------------

    /**
     * The editor descriptors and the parser's verb set must name the same verbs.
     * A verb the parser handles but no descriptor covers cannot be authored in
     * the web editor and is missing from the generated schema; a descriptor with
     * no parser arm offers pack authors a verb that silently does nothing. Both
     * halves of that drift have already happened once here, which is how the
     * harness ended up warning about origins:food years after it was implemented.
     */
    @Test
    void descriptorsAndParserAgreeOnTheVerbSet() {
        Set<String> described = new HashSet<>();
        BuiltinItemConditions.descriptors().forEach((id, type) -> {
            described.add(id.toString());
            type.aliases().forEach(a -> described.add(a.toString()));
        });
        assertEquals(ItemConditionParser.KNOWN_TYPES, described);
    }
}
