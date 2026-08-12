package com.cyberday1.neoorigins.compat.condition;

import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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
 * <p><b>Why this differs from the 1.21.1 copy.</b> There the same assertions run
 * against real vanilla stacks — a diamond chestplate really does report 8 armour,
 * a netherite pickaxe really does report tier 4. That is not reachable here: 26.x
 * moved item default components out of registration into a datapack-reload step
 * ({@code DataComponentInitializers}), and {@code Item.components()} merely
 * delegates to the still-unbound holder, so there is no vanilla default map to
 * read in a bare bootstrap at all. These therefore build the components the
 * helpers read, which still exercises every branch of the resolution logic — the
 * ARMOR/ADD_VALUE filters, the tier-tag table, the damage arithmetic — and leaves
 * "vanilla items are shaped the way we assume" to the 1.21.1 copy and the
 * headless pack-boot gate.
 *
 * <p>One thing this copy tests that 1.21.1 cannot: the {@code copper} tier, which
 * 26.x adds between stone and iron and which Apoli has no number for.
 */
class ItemConditionGapTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // A stack cannot be touched at all while its item's components are
        // unbound. Binding EMPTY is safe precisely because these tests never read
        // a default — every component they assert on is set on the stack below.
        for (Item item : new Item[] { Items.STICK, Items.STONE }) {
            item.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        }
    }

    /** A carrier stack with no components of its own. */
    private static ItemStack bare() {
        return new ItemStack(Items.STICK);
    }

    // ---- armor_value -----------------------------------------------------

    /** Armour lives on {@code minecraft:attribute_modifiers}; {@code ArmorItem} does not exist on 26.x. */
    private static ItemStack withArmor(double amount) {
        ItemStack s = bare();
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY.withModifierAdded(
            Attributes.ARMOR,
            new AttributeModifier(Identifier.parse("neoorigins:test_armor"),
                amount, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.ANY));
        return s;
    }

    @Test
    void armorValueReadsTheStacksOwnArmour() {
        assertEquals(8.0, ItemConditionParser.armorValueOf(withArmor(8.0)));
        assertEquals(2.0, ItemConditionParser.armorValueOf(withArmor(2.0)));
    }

    @Test
    void armorValueIsZeroForThingsThatArentArmour() {
        assertEquals(0.0, ItemConditionParser.armorValueOf(bare()));
        assertEquals(0.0, ItemConditionParser.armorValueOf(ItemStack.EMPTY));
    }

    /**
     * Only flat {@code ADD_VALUE} bonuses on {@code minecraft:armor} count. A
     * multiplier has no meaning without a base to apply it to, and a toughness
     * modifier is a different stat that would otherwise be silently added in.
     */
    @Test
    void armorValueIgnoresOtherAttributesAndNonFlatOperations() {
        ItemStack s = bare();
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY
            .withModifierAdded(Attributes.ARMOR,
                new AttributeModifier(Identifier.parse("neoorigins:flat"),
                    3.0, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.ANY)
            .withModifierAdded(Attributes.ARMOR,
                new AttributeModifier(Identifier.parse("neoorigins:scaled"),
                    5.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                EquipmentSlotGroup.ANY)
            .withModifierAdded(Attributes.ARMOR_TOUGHNESS,
                new AttributeModifier(Identifier.parse("neoorigins:toughness"),
                    9.0, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.ANY));
        assertEquals(3.0, ItemConditionParser.armorValueOf(s));
    }

    @Test
    void armorValueConditionDiscriminates() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "origins:armor_value");
        json.addProperty("comparison", ">");
        json.addProperty("compare_to", 2);
        ItemCondition cond = ItemConditionParser.parse(json);

        assertTrue(cond.test(withArmor(8.0)), "8 armour must satisfy > 2");
        assertFalse(cond.test(withArmor(2.0)), "2 armour must not satisfy > 2");
        assertFalse(cond.test(bare()),
            "a non-armour stack must not satisfy > 2 — this is the fail-open regression");
    }

    // ---- harvest_level ---------------------------------------------------

    /**
     * A tool of the given vanilla tier. The tier is named by the deny rule
     * ("cannot drop these blocks"), which is what replaced numeric harvest levels
     * in 1.20.5 — the mining-speed rules are keyed on material and say nothing
     * about tier.
     */
    private static ItemStack withTier(String incorrectForTag) {
        TagKey<Block> tag = TagKey.create(Registries.BLOCK,
            Identifier.withDefaultNamespace(incorrectForTag));
        HolderSet<Block> blocks = HolderSet.emptyNamed(BuiltInRegistries.BLOCK, tag);
        ItemStack s = bare();
        s.set(DataComponents.TOOL, new Tool(
            List.of(new Tool.Rule(blocks, Optional.empty(), Optional.of(false))), 1.0f, 1, true));
        return s;
    }

    @Test
    void harvestLevelRecoversApolisTierNumbering() {
        assertEquals(0, ItemConditionParser.harvestLevelOf(withTier("incorrect_for_wooden_tool")));
        // Gold shares wood's level in Apoli despite mining faster.
        assertEquals(0, ItemConditionParser.harvestLevelOf(withTier("incorrect_for_gold_tool")));
        assertEquals(1, ItemConditionParser.harvestLevelOf(withTier("incorrect_for_stone_tool")));
        assertEquals(2, ItemConditionParser.harvestLevelOf(withTier("incorrect_for_iron_tool")));
        assertEquals(3, ItemConditionParser.harvestLevelOf(withTier("incorrect_for_diamond_tool")));
        assertEquals(4, ItemConditionParser.harvestLevelOf(withTier("incorrect_for_netherite_tool")));
    }

    /**
     * 26.x adds a copper tier between stone and iron. Apoli predates it and has no
     * number for it, so it shares stone's rather than shifting every tier above it
     * and silently re-tuning every pack that gates on iron.
     */
    @Test
    void copperSharesStonesLevelRatherThanShiftingTheScale() {
        assertEquals(1, ItemConditionParser.harvestLevelOf(withTier("incorrect_for_copper_tool")));
        assertEquals(2, ItemConditionParser.harvestLevelOf(withTier("incorrect_for_iron_tool")));
    }

    @Test
    void harvestLevelIsZeroForNonTools() {
        assertEquals(0, ItemConditionParser.harvestLevelOf(bare()));
        assertEquals(0, ItemConditionParser.harvestLevelOf(ItemStack.EMPTY));
    }

    /**
     * A speed rule is not a tier rule. A tool component carrying only mining-speed
     * entries must report 0, not the level of whatever tag it happens to name.
     */
    @Test
    void harvestLevelIgnoresMiningSpeedRules() {
        TagKey<Block> tag = TagKey.create(Registries.BLOCK,
            Identifier.withDefaultNamespace("incorrect_for_netherite_tool"));
        ItemStack s = bare();
        s.set(DataComponents.TOOL, new Tool(
            List.of(Tool.Rule.minesAndDrops(HolderSet.emptyNamed(BuiltInRegistries.BLOCK, tag), 8.0f)),
            1.0f, 1, true));
        assertEquals(0, ItemConditionParser.harvestLevelOf(s));
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

        assertTrue(cond.test(withTier("incorrect_for_netherite_tool")));
        assertFalse(cond.test(withTier("incorrect_for_diamond_tool")));
        assertFalse(cond.test(withTier("incorrect_for_wooden_tool")));
    }

    // ---- durability ------------------------------------------------------

    @Test
    void durabilityIsWhatIsLeftNotWhatItStartedWith() {
        ItemStack pick = bare();
        pick.set(DataComponents.MAX_DAMAGE, 250);
        pick.set(DataComponents.DAMAGE, 0);
        assertEquals(250, ItemConditionParser.remainingDurabilityOf(pick));

        pick.set(DataComponents.DAMAGE, 10);
        assertEquals(240, ItemConditionParser.remainingDurabilityOf(pick));
    }

    @Test
    void durabilityIsZeroForUndamageableItems() {
        assertEquals(0, ItemConditionParser.remainingDurabilityOf(bare()));
        assertEquals(0, ItemConditionParser.remainingDurabilityOf(ItemStack.EMPTY));
    }

    // ---- meat ------------------------------------------------------------

    /**
     * Only the negative half is assertable here: {@code meat} resolves through
     * the NeoForge food tags, and tags are unbound until a server loads them, so
     * every {@code is(TagKey)} is false in a bare bootstrap. A food item returning
     * false below would therefore NOT be evidence of correctness, which is exactly
     * why this test does not assert it — the positive case is covered by the
     * headless pack-boot gate instead.
     */
    @Test
    void meatRejectsNonFoodAndEmptyStacks() {
        assertFalse(ItemConditionParser.isMeat(bare()));
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
