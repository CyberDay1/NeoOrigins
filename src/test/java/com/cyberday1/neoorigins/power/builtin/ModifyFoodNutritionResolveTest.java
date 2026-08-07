package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.power.builtin.ModifyFoodNutritionPower.Config;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With AppleSkin installed, an aquatic origin holding a cod was shown the vanilla
 * 2 hunger / 0.4 saturation rather than the elevated value its
 * {@code modify_food_nutrition} power actually grants. The eat itself was correct —
 * only the preview lied, because the power never rewrites the item's {@code FOOD}
 * data component (it corrects the player's food bar server-side after the fact),
 * and AppleSkin reads that component off the stack on the client.
 *
 * <p>The fix answers AppleSkin's {@code FoodValuesEvent} from
 * {@link ModifyFoodNutritionPower#resolve}, which shares
 * {@link ModifyFoodNutritionPower#scaledSaturation} with the real eat path
 * ({@code applyOverride}). These tests pin that shared math and the resolve
 * semantics — matching, ordering, and the no-op case — so the preview can't drift
 * back out of agreement with what eating actually gives.
 */
class ModifyFoodNutritionResolveTest {

    /** Vanilla cod: 2 nutrition, 0.4 saturation (ratio 0.2 per nutrition point). */
    private static final FoodProperties COD =
        new FoodProperties(2, 0.4F, false, 1.6F, Optional.empty(), List.of());

    private static final TagKey<net.minecraft.world.item.Item> FISHES =
        TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("minecraft", "fishes"));

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Item tags come from a datapack, which a bootstrapped-only harness never
        // loads, so #minecraft:fishes would be empty and every food_tag filter
        // would silently pass as "no match". Bind the one tag these tests use so
        // the tag branch of matchesFilter is genuinely exercised.
        BuiltInRegistries.ITEM.bindTags(Map.of(
            FISHES, List.of(holder(Items.COD), holder(Items.SALMON))
        ));
    }

    private static Holder<net.minecraft.world.item.Item> holder(net.minecraft.world.item.Item item) {
        return BuiltInRegistries.ITEM.wrapAsHolder(item);
    }

    private static Config config(int nutrition, String item, String tag) {
        return new Config(
            nutrition,
            item == null ? Optional.empty() : Optional.of(item),
            tag == null ? Optional.empty() : Optional.of(tag),
            ""
        );
    }

    // ── the shared math ──────────────────────────────────────────────────────

    @Test
    void saturationKeepsThePerNutritionRatio() {
        // Cod carries 0.4 saturation over 2 nutrition = 0.2 each. At 6 nutrition
        // that's 1.2 — the value the eat path adds, so the value the tooltip shows.
        assertEquals(1.2F, ModifyFoodNutritionPower.scaledSaturation(2, 0.4F, 6), 1.0E-5F);
    }

    @Test
    void loweringNutritionLowersSaturationToo() {
        // Caveborn-style penalty diet: 2 nutrition down to 1 halves saturation.
        assertEquals(0.2F, ModifyFoodNutritionPower.scaledSaturation(2, 0.4F, 1), 1.0E-5F);
    }

    @Test
    void zeroNutritionSourceScalesToZeroRatherThanDividingByZero() {
        assertEquals(0.0F, ModifyFoodNutritionPower.scaledSaturation(0, 0.4F, 8), 1.0E-5F);
    }

    @Test
    void overriddenPreservesEveryOtherFoodField() {
        FoodProperties source =
            new FoodProperties(2, 0.4F, true, 3.2F, Optional.of(new ItemStack(Items.BOWL)), List.of());
        FoodProperties out = ModifyFoodNutritionPower.overridden(source, 6);

        assertEquals(6, out.nutrition());
        assertEquals(1.2F, out.saturation(), 1.0E-5F);
        assertTrue(out.canAlwaysEat());
        assertEquals(3.2F, out.eatSeconds(), 1.0E-5F);
        assertTrue(out.usingConvertsTo().isPresent());
        assertEquals(source.effects(), out.effects());
    }

    // ── resolve: filtering ───────────────────────────────────────────────────

    @Test
    void noFilterAppliesToAnyFood() {
        FoodProperties out = ModifyFoodNutritionPower.resolve(
            new ItemStack(Items.COD), COD, List.of(config(6, null, null)));

        assertNotNull(out);
        assertEquals(6, out.nutrition());
        assertEquals(1.2F, out.saturation(), 1.0E-5F);
    }

    @Test
    void foodItemFilterMatchesOnlyThatItem() {
        List<Config> overrides = List.of(config(6, "minecraft:cod", null));

        assertNotNull(ModifyFoodNutritionPower.resolve(new ItemStack(Items.COD), COD, overrides));
        assertNull(ModifyFoodNutritionPower.resolve(new ItemStack(Items.SALMON), COD, overrides));
    }

    @Test
    void foodTagFilterMatchesWithOrWithoutTheLeadingHash() {
        // Authors write "#minecraft:fishes"; matchesFilter strips the '#'. Both
        // spellings must behave the same or the preview would disagree with the
        // eat for half the packs in the wild.
        for (String tag : List.of("#minecraft:fishes", "minecraft:fishes")) {
            List<Config> overrides = List.of(config(6, null, tag));
            assertNotNull(ModifyFoodNutritionPower.resolve(new ItemStack(Items.COD), COD, overrides),
                "cod should match " + tag);
            assertNull(ModifyFoodNutritionPower.resolve(new ItemStack(Items.BREAD), COD, overrides),
                "bread should not match " + tag);
        }
    }

    @Test
    void nonMatchingOverrideLeavesVanillaValuesAlone() {
        // null means "no override applies" — the caller must not touch AppleSkin's
        // modifiedFoodProperties, so the vanilla preview stands.
        assertNull(ModifyFoodNutritionPower.resolve(
            new ItemStack(Items.BREAD), COD, List.of(config(6, "minecraft:cod", null))));
    }

    @Test
    void emptyOverrideListResolvesToNothing() {
        assertNull(ModifyFoodNutritionPower.resolve(new ItemStack(Items.COD), COD, List.of()));
    }

    @Test
    void nullDefaultsResolveToNothing() {
        // A non-food stack has no FOOD component at all.
        assertNull(ModifyFoodNutritionPower.resolve(
            new ItemStack(Items.STONE), null, List.of(config(6, null, null))));
    }

    // ── resolve: ordering, mirroring the server's dispatch ───────────────────

    @Test
    void lastMatchingOverrideWins() {
        // The server walks every matching power and re-applies each from the same
        // pre-eat baseline, so the last one is what the player ends up with.
        FoodProperties out = ModifyFoodNutritionPower.resolve(
            new ItemStack(Items.COD), COD,
            List.of(config(6, null, null), config(10, "minecraft:cod", null)));

        assertNotNull(out);
        assertEquals(10, out.nutrition());
        assertEquals(2.0F, out.saturation(), 1.0E-5F);
    }

    @Test
    void anOverrideEqualToTheFoodsOwnNutritionIsANoOpNotACancel() {
        // applyOverride early-returns when target == original, so it neither
        // changes anything nor undoes a previous power's override. resolve has to
        // behave the same way or the preview would flip back to vanilla.
        FoodProperties out = ModifyFoodNutritionPower.resolve(
            new ItemStack(Items.COD), COD,
            List.of(config(6, null, null), config(2, null, null)));

        assertNotNull(out, "the equal-nutrition override must not cancel the earlier one");
        assertEquals(6, out.nutrition());
    }

    @Test
    void aLoneEqualNutritionOverrideResolvesToNothing() {
        assertNull(ModifyFoodNutritionPower.resolve(
            new ItemStack(Items.COD), COD, List.of(config(2, null, null))));
    }

    // ── the parity guarantee ─────────────────────────────────────────────────

    @Test
    void resolveUsesTheSameSaturationMathAsTheEatPath() {
        // applyOverride computes its saturation delta through scaledSaturation;
        // so does resolve, via overridden(). This asserts they stay the same
        // expression — if someone re-inlines the math into either path, the
        // preview and the eat can drift and the original bug returns.
        int target = 7;
        FoodProperties out = ModifyFoodNutritionPower.resolve(
            new ItemStack(Items.COD), COD, List.of(config(target, null, null)));

        assertNotNull(out);
        assertEquals(
            ModifyFoodNutritionPower.scaledSaturation(COD.nutrition(), COD.saturation(), target),
            out.saturation(),
            0.0F);
    }

    @Test
    void resolveDoesNotMutateTheSuppliedDefaults() {
        FoodProperties defaults = COD;
        FoodProperties out = ModifyFoodNutritionPower.resolve(
            new ItemStack(Items.COD), defaults, List.of(config(6, null, null)));

        assertNotNull(out);
        assertEquals(2, defaults.nutrition());
        assertEquals(0.4F, defaults.saturation(), 1.0E-5F);
        assertSame(COD, defaults);
    }
}
