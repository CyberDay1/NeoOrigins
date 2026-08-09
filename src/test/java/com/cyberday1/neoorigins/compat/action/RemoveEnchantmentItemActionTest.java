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
        // "Components not bound yet". Only one stack is built here and it sets
        // both components it reads, so an empty default map is bound rather
        // than running the real initializer pass, which NeoForge's component
        // validator rejects outside a server context. Same treatment
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
}
