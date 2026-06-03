package com.cyberday1.neoorigins.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A crafting recipe wrapper that delegates to an {@code inner} crafting recipe
 * but additionally requires the crafting {@link Player} (read from
 * {@link OriginCraftingContext}) to satisfy every gate in {@link #gates()}.
 *
 * <p>Both load-time visibility and recipe-book sync work as if this were a
 * normal recipe: it ships to clients via the recipe-book payload, shows in JEI
 * / REI alongside vanilla recipes (we delegate {@link #display()} and
 * {@link #placementInfo()} to the inner recipe), and only the {@link #matches}
 * call short-circuits to {@code false} when the player fails any gate.
 *
 * <p>When evaluated off the crafting tick (e.g. recipe-book auto-fill from a
 * datapack reload before any menu is open), {@link OriginCraftingContext#current()}
 * returns {@code null} and we fall back to <em>deny</em> — the recipe will not
 * match. This is the conservative default and avoids leaking gated outputs to
 * non-qualifying players via auto-craft helpers.
 *
 * <p>The recipe form on disk is:
 * <pre>
 * {
 *   "type": "neoorigins:origin_gated_crafting",
 *   "gates": [ { "type": "neoorigins:has_origin", "origin": "neoorigins:human" } ],
 *   "inner": { "type": "minecraft:crafting_shaped", ... }
 * }
 * </pre>
 *
 * <p>{@code inner} must be a {@link CraftingRecipe}; cooking variants are not
 * yet supported by this serializer (see {@code docs/RECIPE_CONDITIONS.md}).
 *
 * <p><b>26.1 note:</b> the {@link net.minecraft.world.item.crafting.Recipe}
 * interface dropped {@code getResultItem}, {@code getIngredients} and
 * {@code canCraftInDimensions} (recipe-book/JEI presentation now flows through
 * {@link #placementInfo()} / {@link #display()}), and {@code assemble} lost its
 * {@code HolderLookup.Provider} parameter. All presentation methods delegate to
 * {@code inner} so the gated recipe is indistinguishable from its wrapped form
 * apart from the per-player gate check.
 */
public final class OriginGatedRecipe implements CraftingRecipe {

    private final List<OriginGate> gates;
    private final CraftingRecipe inner;

    public OriginGatedRecipe(List<OriginGate> gates, CraftingRecipe inner) {
        this.gates = List.copyOf(gates);
        this.inner = inner;
    }

    public List<OriginGate> gates() { return gates; }
    public CraftingRecipe inner()    { return inner; }

    private boolean passesGates(Player player) {
        if (player == null) return false;
        for (OriginGate gate : gates) {
            if (!gate.test(player)) return false;
        }
        return true;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        // If the player can't be resolved (e.g. recipe-book preflight, server
        // startup recipe sanity check), default to deny so gated outputs never
        // leak. The CraftingMenu mixin plants the player just before every
        // slotChangedCraftingGrid call (which is where getRecipeFor -> matches
        // runs on 26.1), so real craft attempts hit the happy path.
        Player player = OriginCraftingContext.current();
        if (!passesGates(player)) return false;
        return inner.matches(input, level);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return inner.assemble(input);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return inner.getRemainingItems(input);
    }

    @Override
    public CraftingBookCategory category() {
        return inner.category();
    }

    @Override
    public String group() {
        return inner.group();
    }

    @Override
    public boolean showNotification() {
        return inner.showNotification();
    }

    @Override
    public PlacementInfo placementInfo() {
        return inner.placementInfo();
    }

    @Override
    public List<RecipeDisplay> display() {
        return inner.display();
    }

    @Override
    public RecipeSerializer<? extends CraftingRecipe> getSerializer() {
        return OriginRecipeRegistry.ORIGIN_GATED_CRAFTING_SERIALIZER.get();
    }

    @Override
    public boolean isSpecial() {
        // Mark as "special" so the recipe book doesn't aggressively flag this
        // recipe as completable based on inventory alone — without this, the
        // book might highlight it as craftable for players who can't satisfy
        // the gate. Slightly suboptimal UX (no auto-fill arrow) but correct.
        return true;
    }

    public static Identifier typeId() {
        return Identifier.fromNamespaceAndPath("neoorigins", "origin_gated_crafting");
    }
}
