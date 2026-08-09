package com.cyberday1.neoorigins.compat.action;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code origins:remove_enchantment} was never implemented, so a legacy
 * "Remove Curse" power loaded, gated and ticked correctly and then did
 * nothing at all: the item-action parser fell through to its default branch
 * and handed back a no-op.
 *
 * <p>The power in question writes the field shapes the upstream Apoli docs do
 * not: a plural {@code enchantments} array rather than a singular
 * {@code enchantment} string, and {@code reset_repair_cost} on the enclosing
 * {@code equipped_item_action} rather than on the item action itself. Both
 * shapes are covered here alongside the documented ones, so being wrong about
 * which one upstream really blesses costs nothing.
 *
 * <p>These assert against the real resolution helpers rather than only that
 * parsing succeeds - a parse-only test passes on a parser that recognises the
 * verb and then resolves no ids, which is the live defect all over again
 * (same reason {@code BreathOutOfFluidPower.resolveIntervalTicks} is exposed).
 */
class RemoveEnchantmentItemActionTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // 26.x moved an item's default components out of registration and into
        // a datapack-reload step (ReloadableServerResources runs
        // DataComponentInitializers), so after a bare Bootstrap every Item
        // holder is component-unbound and `new ItemStack(...)` throws
        // "Components not bound yet". The stacks built here set both components
        // they read, so an empty default map is bound rather than running the
        // real initializer pass, which NeoForge's component validator rejects
        // outside a server context. Same treatment
        // LegacyMcFunctionRewriteTest already gives Items.POTION.
        Items.DIAMOND_SWORD.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
    }

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    private static Identifier mc(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }

    /**
     * A {@link Holder} that carries a registry key but no value, which is all
     * the removal predicate looks at. Enchantments are datapack registry
     * entries on 1.21+, so there is no static registry to pull real holders
     * from in a plain unit test.
     */
    private static Holder<Enchantment> holder(String path) {
        HolderOwner<Enchantment> owner = new HolderOwner<>() {};
        return Holder.Reference.createStandAlone(
            owner, ResourceKey.create(Registries.ENCHANTMENT, mc(path)));
    }

    private static ItemEnchantments.Mutable enchanted(String... paths) {
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (String path : paths) mutable.set(holder(path), 1);
        return mutable;
    }

    /** Levelled variant of {@link #enchanted}: alternating path, level pairs. */
    private static ItemEnchantments.Mutable levelled(Object... pathsAndLevels) {
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (int i = 0; i < pathsAndLevels.length; i += 2) {
            mutable.set(holder((String) pathsAndLevels[i]), (Integer) pathsAndLevels[i + 1]);
        }
        return mutable;
    }

    /**
     * Level of one enchantment left on the map, or 0 when it is gone. Looked up
     * through {@code keySet} rather than by building a fresh {@link Holder},
     * because {@code Holder.Reference} compares by identity: a second holder for
     * the same key would miss and read as 0 whatever the map actually holds.
     */
    private static int levelOf(ItemEnchantments.Mutable mutable, String path) {
        return mutable.keySet().stream()
            .filter(h -> h.unwrapKey().orElseThrow().identifier().getPath().equals(path))
            .mapToInt(mutable::getLevel)
            .findFirst()
            .orElse(0);
    }

    /** {@link #levelOf} against the immutable component read back off a stack. */
    private static int levelOf(ItemEnchantments enchantments, String path) {
        return enchantments.keySet().stream()
            .filter(h -> h.unwrapKey().orElseThrow().identifier().getPath().equals(path))
            .mapToInt(enchantments::getLevel)
            .findFirst()
            .orElse(0);
    }

    private static Set<String> pathsIn(ItemEnchantments.Mutable mutable) {
        return mutable.keySet().stream()
            .map(h -> h.unwrapKey().orElseThrow().identifier().getPath())
            .collect(java.util.stream.Collectors.toSet());
    }

    // ----- recognition -----

    @Test
    void typeIsRecognisedByTheParser() {
        assertTrue(ItemActionParser.KNOWN_TYPES.contains("neoorigins:remove_enchantment"),
            "remove_enchantment must be in KNOWN_TYPES so the golden-master harness sees it");
    }

    @Test
    void parsingTheVerbDoesNotYieldTheNoopFallback() {
        // The old behaviour: unrecognised type -> ItemAction.noop(). A no-op
        // lambda and a real action are both ItemAction, so assert on effect.
        ItemEnchantments.Mutable mutable = enchanted("binding_curse", "sharpness");
        ItemActionParser.removeMatching(mutable,
            ItemActionParser.resolveEnchantmentIds(json("""
                {"type":"origins:remove_enchantment","enchantments":["minecraft:binding_curse"]}
                """)));
        assertEquals(Set.of("sharpness"), pathsIn(mutable));
    }

    // ----- field shape: singular vs plural -----

    @Test
    void singularEnchantmentStringResolves() {
        // The shape origins.readthedocs.io documents.
        assertEquals(List.of(mc("mending")),
            ItemActionParser.resolveEnchantmentIds(json("""
                {"type":"origins:remove_enchantment","enchantment":"minecraft:mending",
                 "reset_repair_cost":true}
                """)));
    }

    @Test
    void pluralEnchantmentsArrayResolves() {
        // The shape the user's 1.20.1 Remove Curse power actually ships.
        assertEquals(List.of(mc("vanishing_curse"), mc("binding_curse")),
            ItemActionParser.resolveEnchantmentIds(json("""
                {"type":"origins:remove_enchantment",
                 "enchantments":["minecraft:vanishing_curse","minecraft:binding_curse"]}
                """)));
    }

    @Test
    void bothKeysAtOnceUnionWithoutDuplicates() {
        assertEquals(List.of(mc("mending"), mc("binding_curse")),
            ItemActionParser.resolveEnchantmentIds(json("""
                {"type":"origins:remove_enchantment","enchantment":"minecraft:mending",
                 "enchantments":["minecraft:binding_curse","minecraft:mending"]}
                """)));
    }

    @Test
    void singularKeyAlsoAcceptsAnArray() {
        assertEquals(List.of(mc("binding_curse")),
            ItemActionParser.resolveEnchantmentIds(json("""
                {"type":"origins:remove_enchantment","enchantment":["minecraft:binding_curse"]}
                """)));
    }

    @Test
    void noEnchantmentKeyResolvesToNothing() {
        assertEquals(List.of(),
            ItemActionParser.resolveEnchantmentIds(json("""
                {"type":"origins:remove_enchantment","reset_repair_cost":true}
                """)));
    }

    // ----- field shape: reset_repair_cost on either side -----

    @Test
    void resetRepairCostInsideTheItemAction() {
        // Upstream position.
        assertTrue(ItemActionParser.resolveResetRepairCost(json("""
            {"type":"origins:remove_enchantment","enchantment":"minecraft:mending",
             "reset_repair_cost":true}
            """), null));
    }

    @Test
    void resetRepairCostOnTheEnclosingEquippedItemAction() {
        // The user's position: sibling of item_action, not inside it.
        JsonObject enclosing = json("""
            {"type":"origins:equipped_item_action","equipment_slot":"mainhand",
             "item_action":{"type":"origins:remove_enchantment",
                            "enchantments":["minecraft:binding_curse"]},
             "reset_repair_cost":true}
            """);
        assertTrue(ItemActionParser.resolveResetRepairCost(
            enclosing.getAsJsonObject("item_action"), enclosing));
    }

    @Test
    void resetRepairCostDefaultsOffAndEitherPositionCanWin() {
        String off = "{\"type\":\"origins:remove_enchantment\",\"reset_repair_cost\":false}";
        assertFalse(ItemActionParser.resolveResetRepairCost(
            json("{\"type\":\"origins:remove_enchantment\"}"), null));
        assertFalse(ItemActionParser.resolveResetRepairCost(
            json(off), json("{\"reset_repair_cost\":false}")));
        assertTrue(ItemActionParser.resolveResetRepairCost(
            json(off), json("{\"reset_repair_cost\":true}")));
    }

    // ----- degradation -----

    @Test
    void malformedIdIsSkippedAndTheRestSurvive() {
        // One bad entry must not throw or take the whole power down.
        assertEquals(List.of(mc("binding_curse")),
            ItemActionParser.resolveEnchantmentIds(json("""
                {"type":"origins:remove_enchantment",
                 "enchantments":["NOT A VALID ID","minecraft:binding_curse"]}
                """)));
    }

    @Test
    void unregisteredIdMatchesNothingAndLeavesTheStackAlone() {
        // No registry is reachable from an ItemAction, so an id nobody
        // registered simply never matches rather than blowing up.
        ItemEnchantments.Mutable mutable = enchanted("binding_curse", "sharpness");
        ItemActionParser.removeMatching(mutable,
            ItemActionParser.resolveEnchantmentIds(json("""
                {"type":"origins:remove_enchantment",
                 "enchantments":["somemod:no_such_enchantment"]}
                """)));
        assertEquals(Set.of("binding_curse", "sharpness"), pathsIn(mutable));
    }

    // ----- removal -----

    @Test
    void removesEveryNamedCurseAndKeepsTheRest() {
        // The user's power, end to end over the resolution + removal helpers.
        JsonObject enclosing = json("""
            {"type":"origins:equipped_item_action","equipment_slot":"mainhand",
             "item_action":{"type":"origins:remove_enchantment",
                            "enchantments":["minecraft:vanishing_curse",
                                            "minecraft:binding_curse"]},
             "reset_repair_cost":true}
            """);
        JsonObject itemAction = enclosing.getAsJsonObject("item_action");

        ItemEnchantments.Mutable mutable =
            enchanted("vanishing_curse", "binding_curse", "unbreaking");
        ItemActionParser.removeMatching(mutable,
            ItemActionParser.resolveEnchantmentIds(itemAction));

        assertEquals(Set.of("unbreaking"), pathsIn(mutable));
        assertTrue(ItemActionParser.resolveResetRepairCost(itemAction, enclosing));
    }

    @Test
    void theReportedPowersJsonStripsBothCursesOffARealStack() {
        // Byte-for-byte one slot of the user's 1.20.1 Remove Curse power,
        // straight through the public parse entry point onto a real ItemStack.
        JsonObject enclosing = json("""
            {
               "type":"origins:equipped_item_action",
               "equipment_slot":"mainhand",
               "item_action":{
                  "type":"origins:remove_enchantment",
                  "enchantments":[
                     "minecraft:vanishing_curse",
                     "minecraft:binding_curse"
                  ]
               },
               "reset_repair_cost":true
            }
            """);

        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        ItemEnchantments.Mutable before = enchanted("vanishing_curse", "binding_curse", "sharpness");
        stack.set(DataComponents.ENCHANTMENTS, before.toImmutable());
        stack.set(DataComponents.REPAIR_COST, 7);

        ItemAction action = ItemActionParser.parse(
            enclosing.getAsJsonObject("item_action"), enclosing);
        action.execute(stack);

        Set<String> left = stack.getEnchantments().keySet().stream()
            .map(h -> h.unwrapKey().orElseThrow().identifier().getPath())
            .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("sharpness"), left, "both curses should be gone, sharpness kept");
        assertEquals(0, stack.getOrDefault(DataComponents.REPAIR_COST, 0),
            "reset_repair_cost on the equipped_item_action should clear the anvil cost");
    }

    // ----- levels: reduce rather than remove -----
    //
    // Upstream's fourth field, and the one this parser used to ignore. It is a
    // number of levels to SUBTRACT: an enchantment above it is reduced, one at
    // or below it is stripped. Ignoring it was silent over-removal, so the
    // absent case below is a regression guard on the behaviour that predates it.

    @Test
    void levelsIsAbsentByDefaultAndPresentWhenWritten() {
        // Absent and 0 are different things: 0 must not read as "no field".
        assertTrue(ItemActionParser.resolveLevels(json("""
            {"type":"origins:remove_enchantment","enchantment":"minecraft:sharpness"}
            """)).isEmpty());
        assertEquals(0, ItemActionParser.resolveLevels(json("""
            {"type":"origins:remove_enchantment","levels":0}
            """)).orElse(-999));
        assertEquals(2, ItemActionParser.resolveLevels(json("""
            {"type":"origins:remove_enchantment","levels":2}
            """)).orElse(-999));
    }

    @Test
    void nonNumericLevelsWarnsAndReadsAsAbsent() {
        // Upstream fails the whole power to load; this parser is fail-soft, so
        // the field is dropped and removal stays outright.
        assertTrue(ItemActionParser.resolveLevels(json("""
            {"type":"origins:remove_enchantment","levels":"lots"}
            """)).isEmpty());
        assertTrue(ItemActionParser.resolveLevels(json("""
            {"type":"origins:remove_enchantment","levels":{"a":1}}
            """)).isEmpty());
    }

    @Test
    void levelsAbsentStillRemovesOutright() {
        // Regression guard: no levels field means a Sharpness V goes entirely,
        // which is the only thing packs written before the field can expect.
        ItemEnchantments.Mutable mutable = levelled("sharpness", 5);
        JsonObject action = json("""
            {"type":"origins:remove_enchantment","enchantment":"minecraft:sharpness"}
            """);
        ItemActionParser.removeMatching(mutable,
            ItemActionParser.resolveEnchantmentIds(action),
            ItemActionParser.resolveLevels(action));
        assertEquals(Set.of(), pathsIn(mutable), "with no levels field it must come off outright");
    }

    @Test
    void levelsBelowCurrentLevelReducesIt() {
        // Sharpness V minus one level is Sharpness IV, not "no Sharpness".
        ItemEnchantments.Mutable mutable = levelled("sharpness", 5);
        JsonObject action = json("""
            {"type":"origins:remove_enchantment","enchantment":"minecraft:sharpness","levels":1}
            """);
        ItemActionParser.removeMatching(mutable,
            ItemActionParser.resolveEnchantmentIds(action),
            ItemActionParser.resolveLevels(action));
        assertEquals(Set.of("sharpness"), pathsIn(mutable), "it must survive, not be stripped");
        assertEquals(4, levelOf(mutable, "sharpness"));
    }

    @Test
    void levelsEqualToCurrentLevelRemovesIt() {
        ItemEnchantments.Mutable mutable = levelled("sharpness", 1, "unbreaking", 3);
        JsonObject action = json("""
            {"type":"origins:remove_enchantment",
             "enchantments":["minecraft:sharpness","minecraft:unbreaking"],"levels":3}
            """);
        ItemActionParser.removeMatching(mutable,
            ItemActionParser.resolveEnchantmentIds(action),
            ItemActionParser.resolveLevels(action));
        // unbreaking III is exactly at the boundary and goes; sharpness I is
        // below it and goes too.
        assertEquals(Set.of(), pathsIn(mutable));
    }

    @Test
    void levelsAboveCurrentLevelRemovesIt() {
        // Over-subtracting must not leave a zero or negative level behind.
        ItemEnchantments.Mutable mutable = levelled("sharpness", 2);
        JsonObject action = json("""
            {"type":"origins:remove_enchantment","enchantment":"minecraft:sharpness","levels":5}
            """);
        ItemActionParser.removeMatching(mutable,
            ItemActionParser.resolveEnchantmentIds(action),
            ItemActionParser.resolveLevels(action));
        assertEquals(Set.of(), pathsIn(mutable));
        assertEquals(0, levelOf(mutable, "sharpness"));
    }

    @Test
    void levelsZeroSubtractsNothing() {
        // Degenerate but well defined: current - 0 is current, and nothing is
        // at or below zero. Must not read as "remove outright".
        ItemEnchantments.Mutable mutable = levelled("sharpness", 3);
        JsonObject action = json("""
            {"type":"origins:remove_enchantment","enchantment":"minecraft:sharpness","levels":0}
            """);
        ItemActionParser.removeMatching(mutable,
            ItemActionParser.resolveEnchantmentIds(action),
            ItemActionParser.resolveLevels(action));
        assertEquals(Set.of("sharpness"), pathsIn(mutable));
        assertEquals(3, levelOf(mutable, "sharpness"));
    }

    @Test
    void negativeLevelsRaisesTheLevelAsUpstreamsArithmeticDoes() {
        // Deliberately unclamped: upstream subtracts without validating, so a
        // pack that shipped a negative got a level-up and must keep getting one.
        ItemEnchantments.Mutable mutable = levelled("sharpness", 3);
        JsonObject action = json("""
            {"type":"origins:remove_enchantment","enchantment":"minecraft:sharpness","levels":-2}
            """);
        ItemActionParser.removeMatching(mutable,
            ItemActionParser.resolveEnchantmentIds(action),
            ItemActionParser.resolveLevels(action));
        assertEquals(5, levelOf(mutable, "sharpness"));
    }

    @Test
    void levelsOnlyTouchesTheNamedEnchantmentsOfAMultiEnchantmentStack() {
        ItemEnchantments.Mutable mutable =
            levelled("sharpness", 5, "binding_curse", 1, "unbreaking", 3, "mending", 1);
        JsonObject action = json("""
            {"type":"origins:remove_enchantment",
             "enchantments":["minecraft:sharpness","minecraft:binding_curse"],"levels":1}
            """);
        ItemActionParser.removeMatching(mutable,
            ItemActionParser.resolveEnchantmentIds(action),
            ItemActionParser.resolveLevels(action));
        // sharpness reduced, binding_curse stripped, the two untargeted ones
        // untouched at their original levels.
        assertEquals(Set.of("sharpness", "unbreaking", "mending"), pathsIn(mutable));
        assertEquals(4, levelOf(mutable, "sharpness"));
        assertEquals(3, levelOf(mutable, "unbreaking"));
        assertEquals(1, levelOf(mutable, "mending"));
    }

    @Test
    void levelsReducesThroughThePublicParseEntryPointOnARealStack() {
        // End to end: the field has to survive parse(), not just the helper.
        JsonObject enclosing = json("""
            {
               "type":"origins:equipped_item_action",
               "equipment_slot":"mainhand",
               "item_action":{
                  "type":"origins:remove_enchantment",
                  "enchantments":["minecraft:sharpness","minecraft:binding_curse"],
                  "levels":2
               }
            }
            """);

        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.set(DataComponents.ENCHANTMENTS,
            levelled("sharpness", 5, "binding_curse", 1, "unbreaking", 3).toImmutable());

        ItemActionParser.parse(enclosing.getAsJsonObject("item_action"), enclosing).execute(stack);

        ItemEnchantments left = stack.getEnchantments();
        assertEquals(Set.of("sharpness", "unbreaking"),
            left.keySet().stream()
                .map(h -> h.unwrapKey().orElseThrow().identifier().getPath())
                .collect(java.util.stream.Collectors.toSet()),
            "binding_curse I is at or below 2 and goes; sharpness V is reduced, not stripped");
        assertEquals(3, levelOf(left, "sharpness"), "V minus 2 is III");
        assertEquals(3, levelOf(left, "unbreaking"), "untargeted, so untouched");
    }
}
