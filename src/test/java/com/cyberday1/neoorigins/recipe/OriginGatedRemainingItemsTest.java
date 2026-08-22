package com.cyberday1.neoorigins.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for the community "billions of arcane_essence on the ground" report.
 *
 * <p>Root cause: {@code ResultSlot.onTake} re-resolves the crafting recipe to
 * compute leftover items. For an {@link OriginGatedRecipe}, that resolve ran with
 * no {@link OriginCraftingContext} player planted (the old context mixin only
 * wrapped {@code slotChangedCraftingGrid}), so {@code matches} failed closed,
 * {@code getRecipeFor} returned empty, and vanilla fell back to
 * {@code copyAllInputItems(input)} — echoing the ENTIRE input grid back as
 * "remaining items". {@code onTake} then re-grew the consumed ingredient
 * (Origins-Classes' {@code common_ink} consumes {@code arcane_essence}), snowballing
 * the stack until it overflowed the [1;99] ItemStack save codec.
 *
 * <p>The fix has two parts: a {@code ResultSlot.onTake} context mixin so the gate
 * resolves for real, and an {@link OriginGatedRecipe#getRemainingItems} override
 * delegating to the inner recipe. This test locks the recipe-level invariant of
 * the second part: a gated recipe that consumes an ingredient must report NO
 * leftover for that ingredient — never echo the input grid back.
 *
 * <p>Uses vanilla {@code minecraft:lapis_lazuli} as the consumed ingredient (the
 * mechanism is item-agnostic; the harness has no {@code irons_spellbooks}).
 *
 * <p><b>Why this differs from the 1.21.1 copy.</b> Two 26.x changes, neither of
 * which touches what is being proved. Ingredients are registry-backed holder sets
 * here, so the decode runs through {@link RegistryOps} the way
 * {@code InlineRecipeRegistry} does rather than through bare {@code JsonOps}. And
 * item default components moved out of registration into a datapack-reload step, so
 * a stack cannot be constructed until its item's components are bound; EMPTY is
 * enough because the leftover computation reads the item's crafting remainder, not
 * a component.
 */
class OriginGatedRemainingItemsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (Item item : new Item[] {
            Items.INK_SAC, Items.GLASS_BOTTLE, Items.LAPIS_LAZULI, Items.PAPER }) {
            item.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        }
    }

    // A gated shapeless recipe that CONSUMES lapis (stand-in for arcane_essence),
    // mirroring the reporter's common_ink shape.
    private static final String CONSUMING_RECIPE_JSON = """
        {
          "type": "neoorigins:origin_gated_crafting",
          "gates": [ { "type": "neoorigins:has_origin", "origin": "origins_classes_iss:wizard" } ],
          "inner": {
            "type": "minecraft:crafting_shapeless",
            "ingredients": [
              "minecraft:ink_sac",
              "minecraft:glass_bottle",
              "minecraft:lapis_lazuli"
            ],
            "result": { "id": "minecraft:paper", "count": 1 }
          }
        }
        """;

    private static OriginGatedRecipe decode() {
        JsonObject json = JsonParser.parseString(CONSUMING_RECIPE_JSON).getAsJsonObject();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE,
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        DataResult<OriginGatedRecipe> decoded =
            OriginGatedRecipeSerializer.MAP_CODEC.codec().parse(ops, json);
        assertTrue(decoded.error().isEmpty(),
            () -> "recipe decode failed: " + decoded.error().map(e -> e.message()).orElse("?"));
        return decoded.result().get();
    }

    @Test
    void consumingRecipeReportsNoLeftoverIngredient() {
        OriginGatedRecipe recipe = decode();

        CraftingInput input = CraftingInput.of(3, 1, List.of(
            new ItemStack(Items.INK_SAC),
            new ItemStack(Items.GLASS_BOTTLE),
            new ItemStack(Items.LAPIS_LAZULI)
        ));

        var remaining = recipe.getRemainingItems(input);

        // Every leftover slot must be EMPTY — the consumed lapis (and the other
        // ingredients) must NOT be echoed back. Echoing them back is exactly the
        // copyAllInputItems() runaway that regrew arcane_essence into the billions.
        assertEquals(input.size(), remaining.size(), "leftover list must match input size");
        for (int i = 0; i < remaining.size(); i++) {
            assertTrue(remaining.get(i).isEmpty(),
                "leftover slot " + i + " must be empty (got " + remaining.get(i) + ")");
        }
    }

    @Test
    void wrapperLeftoversEqualInnerLeftovers() {
        OriginGatedRecipe recipe = decode();
        CraftingRecipe inner = recipe.inner();

        CraftingInput input = CraftingInput.of(3, 1, List.of(
            new ItemStack(Items.INK_SAC),
            new ItemStack(Items.GLASS_BOTTLE),
            new ItemStack(Items.LAPIS_LAZULI)
        ));

        var wrapper = recipe.getRemainingItems(input);
        var innerLeftovers = inner.getRemainingItems(input);

        assertEquals(innerLeftovers.size(), wrapper.size(),
            "wrapper must delegate leftover computation to inner");
        for (int i = 0; i < wrapper.size(); i++) {
            assertTrue(ItemStack.matches(innerLeftovers.get(i), wrapper.get(i)),
                "wrapper leftover slot " + i + " must equal inner leftover");
        }
    }

    /**
     * The load-bearing invariant for the actual runaway: {@code ResultSlot.onTake}
     * re-resolves the recipe via {@code getRecipeFor}, which calls
     * {@link OriginGatedRecipe#matches}. If no crafting player is planted in
     * {@link OriginCraftingContext}, the gate fails closed → {@code matches} false
     * → {@code getRecipeFor} empty → vanilla's {@code copyAllInputItems} fallback
     * echoes the input grid back and the consumed ingredient regrows.
     *
     * <p>This reproduces the null-context path (matches must be false) and confirms
     * that the presence of a context player is what flips the outcome — which is
     * exactly what {@code ResultSlotOriginContextMixin} now guarantees during
     * {@code onTake}. (We can't cheaply plant a real qualifying ServerPlayer here,
     * but proving the null-context branch is the deny path pins the mechanism.)
     */
    @Test
    void nullCraftingContextFailsClosed_theRunawayTrigger() {
        OriginGatedRecipe recipe = decode();
        CraftingInput input = CraftingInput.of(3, 1, List.of(
            new ItemStack(Items.INK_SAC),
            new ItemStack(Items.GLASS_BOTTLE),
            new ItemStack(Items.LAPIS_LAZULI)
        ));

        // Ensure no player is planted (mirrors the pre-fix onTake path).
        OriginCraftingContext.pop();
        assertTrue(!recipe.matches(input, null),
            "with no crafting-context player, the gate must fail closed — this is the "
                + "state that made getRecipeFor fall back to copyAllInputItems and regrow "
                + "the consumed ingredient. The onTake mixin now prevents this state.");
    }
}
