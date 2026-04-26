package net.minecraft.world.item.alchemy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.crafting.Ingredient;

public class PotionBrewing {
    public static final int BREWING_TIME_SECONDS = 20;
    public static final PotionBrewing EMPTY = new PotionBrewing(List.of(), List.of(), List.of());
    private final List<Ingredient> containers;
    private final List<PotionBrewing.Mix<Potion>> potionMixes;
    private final List<PotionBrewing.Mix<Item>> containerMixes;
    private final net.neoforged.neoforge.common.brewing.BrewingRecipeRegistry registry;

    PotionBrewing(List<Ingredient> p_340915_, List<PotionBrewing.Mix<Potion>> p_341164_, List<PotionBrewing.Mix<Item>> p_341170_) {
        this(p_340915_, p_341164_, p_341170_, List.of());
    }

    PotionBrewing(List<Ingredient> p_340915_, List<PotionBrewing.Mix<Potion>> p_341164_, List<PotionBrewing.Mix<Item>> p_341170_, List<net.neoforged.neoforge.common.brewing.IBrewingRecipe> recipes) {
        this.containers = p_340915_;
        this.potionMixes = p_341164_;
        this.containerMixes = p_341170_;
        this.registry = new net.neoforged.neoforge.common.brewing.BrewingRecipeRegistry(recipes);
    }

    public boolean isIngredient(ItemStack p_43507_) {
        return this.registry.isValidIngredient(p_43507_) || this.isContainerIngredient(p_43507_) || this.isPotionIngredient(p_43507_);
    }

    /**
     * Checks if an item stack is a valid input for brewing,
     * for use in the lower 3 slots where water bottles would normally go.
     */
    public boolean isInput(ItemStack stack) {
        return this.registry.isValidInput(stack) || isContainer(stack);
    }

    /**
     * Retrieves recipes that use the more general interface.
     * This does NOT include the container and potion mixes.
     */
    public List<net.neoforged.neoforge.common.brewing.IBrewingRecipe> getRecipes() {
        return registry.recipes();
    }

    private boolean isContainer(ItemStack p_341168_) {
        for (Ingredient ingredient : this.containers) {
            if (ingredient.test(p_341168_)) {
                return true;
            }
        }

        return false;
    }

    public boolean isContainerIngredient(ItemStack p_43518_) {
        for (PotionBrewing.Mix<Item> mix : this.containerMixes) {
            if (mix.ingredient.test(p_43518_)) {
                return true;
            }
        }

        return false;
    }

    public boolean isPotionIngredient(ItemStack p_43523_) {
        for (PotionBrewing.Mix<Potion> mix : this.potionMixes) {
            if (mix.ingredient.test(p_43523_)) {
                return true;
            }
        }

        return false;
    }

    public boolean isBrewablePotion(Holder<Potion> p_316354_) {
        for (PotionBrewing.Mix<Potion> mix : this.potionMixes) {
            if (mix.to.is(p_316354_)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasMix(ItemStack p_43509_, ItemStack p_43510_) {
        if (registry.hasOutput(p_43509_, p_43510_)) return true;
        return !this.isContainer(p_43509_) ? false : this.hasContainerMix(p_43509_, p_43510_) || this.hasPotionMix(p_43509_, p_43510_);
    }

    public boolean hasContainerMix(ItemStack p_43520_, ItemStack p_43521_) {
        for (PotionBrewing.Mix<Item> mix : this.containerMixes) {
            if (p_43520_.is(mix.from) && mix.ingredient.test(p_43521_)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasPotionMix(ItemStack p_43525_, ItemStack p_43526_) {
        Optional<Holder<Potion>> optional = p_43525_.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).potion();
        if (optional.isEmpty()) {
            return false;
        } else {
            for (PotionBrewing.Mix<Potion> mix : this.potionMixes) {
                if (mix.from.is(optional.get()) && mix.ingredient.test(p_43526_)) {
                    return true;
                }
            }

            return false;
        }
    }

    public ItemStack mix(ItemStack p_43530_, ItemStack p_43531_) {
        if (p_43531_.isEmpty()) {
            return p_43531_;
        } else {
            var customMix = registry.getOutput(p_43531_, p_43530_); // Parameters are swapped compared to what vanilla passes!
            if (!customMix.isEmpty()) return customMix;
            Optional<Holder<Potion>> optional = p_43531_.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).potion();
            if (optional.isEmpty()) {
                return p_43531_;
            } else {
                for (PotionBrewing.Mix<Item> mix : this.containerMixes) {
                    if (p_43531_.is(mix.from) && mix.ingredient.test(p_43530_)) {
                        return PotionContents.createItemStack(mix.to.value(), optional.get());
                    }
                }

                for (PotionBrewing.Mix<Potion> mix1 : this.potionMixes) {
                    if (mix1.from.is(optional.get()) && mix1.ingredient.test(p_43530_)) {
                        return PotionContents.createItemStack(p_43531_.getItem(), mix1.to);
                    }
                }

                return p_43531_;
            }
        }
    }

    /**
     * @deprecated Use {@link #bootstrap(FeatureFlagSet, net.minecraft.core.RegistryAccess)} instead
     */
    @Deprecated
    public static PotionBrewing bootstrap(FeatureFlagSet p_341301_) {
        return bootstrap(p_341301_, net.minecraft.core.RegistryAccess.EMPTY);
    }

    public static PotionBrewing bootstrap(FeatureFlagSet p_341301_, net.minecraft.core.RegistryAccess registryAccess) {
        PotionBrewing.Builder potionbrewing$builder = new PotionBrewing.Builder(p_341301_);
        addVanillaMixes(potionbrewing$builder);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent(potionbrewing$builder, registryAccess));
        return potionbrewing$builder.build();
    }

    public static void addVanillaMixes(PotionBrewing.Builder p_341215_) {
        p_341215_.addContainer(Items.POTION);
        p_341215_.addContainer(Items.SPLASH_POTION);
        p_341215_.addContainer(Items.LINGERING_POTION);
        p_341215_.addContainerRecipe(Items.POTION, Items.GUNPOWDER, Items.SPLASH_POTION);
        p_341215_.addContainerRecipe(Items.SPLASH_POTION, Items.DRAGON_BREATH, Items.LINGERING_POTION);
        p_341215_.addMix(Potions.WATER, Items.GLOWSTONE_DUST, Potions.THICK);
        p_341215_.addMix(Potions.WATER, Items.REDSTONE, Potions.MUNDANE);
        p_341215_.addMix(Potions.WATER, Items.NETHER_WART, Potions.AWKWARD);
        p_341215_.addStartMix(Items.BREEZE_ROD, Potions.WIND_CHARGED);
        p_341215_.addStartMix(Items.SLIME_BLOCK, Potions.OOZING);
        p_341215_.addStartMix(Items.STONE, Potions.INFESTED);
        p_341215_.addStartMix(Items.COBWEB, Potions.WEAVING);
        p_341215_.addMix(Potions.AWKWARD, Items.GOLDEN_CARROT, Potions.NIGHT_VISION);
        p_341215_.addMix(Potions.NIGHT_VISION, Items.REDSTONE, Potions.LONG_NIGHT_VISION);
        p_341215_.addMix(Potions.NIGHT_VISION, Items.FERMENTED_SPIDER_EYE, Potions.INVISIBILITY);
        p_341215_.addMix(Potions.LONG_NIGHT_VISION, Items.FERMENTED_SPIDER_EYE, Potions.LONG_INVISIBILITY);
        p_341215_.addMix(Potions.INVISIBILITY, Items.REDSTONE, Potions.LONG_INVISIBILITY);
        p_341215_.addStartMix(Items.MAGMA_CREAM, Potions.FIRE_RESISTANCE);
        p_341215_.addMix(Potions.FIRE_RESISTANCE, Items.REDSTONE, Potions.LONG_FIRE_RESISTANCE);
        p_341215_.addStartMix(Items.RABBIT_FOOT, Potions.LEAPING);
        p_341215_.addMix(Potions.LEAPING, Items.REDSTONE, Potions.LONG_LEAPING);
        p_341215_.addMix(Potions.LEAPING, Items.GLOWSTONE_DUST, Potions.STRONG_LEAPING);
        p_341215_.addMix(Potions.LEAPING, Items.FERMENTED_SPIDER_EYE, Potions.SLOWNESS);
        p_341215_.addMix(Potions.LONG_LEAPING, Items.FERMENTED_SPIDER_EYE, Potions.LONG_SLOWNESS);
        p_341215_.addMix(Potions.SLOWNESS, Items.REDSTONE, Potions.LONG_SLOWNESS);
        p_341215_.addMix(Potions.SLOWNESS, Items.GLOWSTONE_DUST, Potions.STRONG_SLOWNESS);
        p_341215_.addMix(Potions.AWKWARD, Items.TURTLE_HELMET, Potions.TURTLE_MASTER);
        p_341215_.addMix(Potions.TURTLE_MASTER, Items.REDSTONE, Potions.LONG_TURTLE_MASTER);
        p_341215_.addMix(Potions.TURTLE_MASTER, Items.GLOWSTONE_DUST, Potions.STRONG_TURTLE_MASTER);
        p_341215_.addMix(Potions.SWIFTNESS, Items.FERMENTED_SPIDER_EYE, Potions.SLOWNESS);
        p_341215_.addMix(Potions.LONG_SWIFTNESS, Items.FERMENTED_SPIDER_EYE, Potions.LONG_SLOWNESS);
        p_341215_.addStartMix(Items.SUGAR, Potions.SWIFTNESS);
        p_341215_.addMix(Potions.SWIFTNESS, Items.REDSTONE, Potions.LONG_SWIFTNESS);
        p_341215_.addMix(Potions.SWIFTNESS, Items.GLOWSTONE_DUST, Potions.STRONG_SWIFTNESS);
        p_341215_.addMix(Potions.AWKWARD, Items.PUFFERFISH, Potions.WATER_BREATHING);
        p_341215_.addMix(Potions.WATER_BREATHING, Items.REDSTONE, Potions.LONG_WATER_BREATHING);
        p_341215_.addStartMix(Items.GLISTERING_MELON_SLICE, Potions.HEALING);
        p_341215_.addMix(Potions.HEALING, Items.GLOWSTONE_DUST, Potions.STRONG_HEALING);
        p_341215_.addMix(Potions.HEALING, Items.FERMENTED_SPIDER_EYE, Potions.HARMING);
        p_341215_.addMix(Potions.STRONG_HEALING, Items.FERMENTED_SPIDER_EYE, Potions.STRONG_HARMING);
        p_341215_.addMix(Potions.HARMING, Items.GLOWSTONE_DUST, Potions.STRONG_HARMING);
        p_341215_.addMix(Potions.POISON, Items.FERMENTED_SPIDER_EYE, Potions.HARMING);
        p_341215_.addMix(Potions.LONG_POISON, Items.FERMENTED_SPIDER_EYE, Potions.HARMING);
        p_341215_.addMix(Potions.STRONG_POISON, Items.FERMENTED_SPIDER_EYE, Potions.STRONG_HARMING);
        p_341215_.addStartMix(Items.SPIDER_EYE, Potions.POISON);
        p_341215_.addMix(Potions.POISON, Items.REDSTONE, Potions.LONG_POISON);
        p_341215_.addMix(Potions.POISON, Items.GLOWSTONE_DUST, Potions.STRONG_POISON);
        p_341215_.addStartMix(Items.GHAST_TEAR, Potions.REGENERATION);
        p_341215_.addMix(Potions.REGENERATION, Items.REDSTONE, Potions.LONG_REGENERATION);
        p_341215_.addMix(Potions.REGENERATION, Items.GLOWSTONE_DUST, Potions.STRONG_REGENERATION);
        p_341215_.addStartMix(Items.BLAZE_POWDER, Potions.STRENGTH);
        p_341215_.addMix(Potions.STRENGTH, Items.REDSTONE, Potions.LONG_STRENGTH);
        p_341215_.addMix(Potions.STRENGTH, Items.GLOWSTONE_DUST, Potions.STRONG_STRENGTH);
        p_341215_.addMix(Potions.WATER, Items.FERMENTED_SPIDER_EYE, Potions.WEAKNESS);
        p_341215_.addMix(Potions.WEAKNESS, Items.REDSTONE, Potions.LONG_WEAKNESS);
        p_341215_.addMix(Potions.AWKWARD, Items.PHANTOM_MEMBRANE, Potions.SLOW_FALLING);
        p_341215_.addMix(Potions.SLOW_FALLING, Items.REDSTONE, Potions.LONG_SLOW_FALLING);
    }

    public static class Builder {
        private final List<Ingredient> containers = new ArrayList<>();
        private final List<PotionBrewing.Mix<Potion>> potionMixes = new ArrayList<>();
        private final List<PotionBrewing.Mix<Item>> containerMixes = new ArrayList<>();
        private final List<net.neoforged.neoforge.common.brewing.IBrewingRecipe> recipes = new ArrayList<>();
        private final FeatureFlagSet enabledFeatures;

        public Builder(FeatureFlagSet p_340975_) {
            this.enabledFeatures = p_340975_;
        }

        private static void expectPotion(Item p_341194_) {
            if (!(p_341194_ instanceof PotionItem)) {
                throw new IllegalArgumentException("Expected a potion, got: " + BuiltInRegistries.ITEM.getKey(p_341194_));
            }
        }

        public void addContainerRecipe(Item p_341264_, Item p_340992_, Item p_341160_) {
            if (p_341264_.isEnabled(this.enabledFeatures) && p_340992_.isEnabled(this.enabledFeatures) && p_341160_.isEnabled(this.enabledFeatures)) {
                expectPotion(p_341264_);
                expectPotion(p_341160_);
                this.containerMixes
                    .add(new PotionBrewing.Mix<>(p_341264_.builtInRegistryHolder(), Ingredient.of(p_340992_), p_341160_.builtInRegistryHolder()));
            }
        }

        public void addContainer(Item p_340911_) {
            if (p_340911_.isEnabled(this.enabledFeatures)) {
                expectPotion(p_340911_);
                this.containers.add(Ingredient.of(p_340911_));
            }
        }

        public void addMix(Holder<Potion> p_341151_, Item p_341216_, Holder<Potion> p_340841_) {
            if (p_341151_.value().isEnabled(this.enabledFeatures)
                && p_341216_.isEnabled(this.enabledFeatures)
                && p_340841_.value().isEnabled(this.enabledFeatures)) {
                this.potionMixes.add(new PotionBrewing.Mix<>(p_341151_, Ingredient.of(p_341216_), p_340841_));
            }
        }

        public void addStartMix(Item p_341103_, Holder<Potion> p_341346_) {
            if (p_341346_.value().isEnabled(this.enabledFeatures)) {
                this.addMix(Potions.WATER, p_341103_, Potions.MUNDANE);
                this.addMix(Potions.AWKWARD, p_341103_, p_341346_);
            }
        }

        /**
         * Adds a new simple brewing recipe.
         *
         * @param input      the ingredient that goes in the same slot as water bottles would
         * @param ingredient the ingredient that goes in the same slot as nether wart would
         * @param output     the item stack that will replace the input once brewing is done
         */
        public void addRecipe(Ingredient input, Ingredient ingredient, ItemStack output) {
            addRecipe(new net.neoforged.neoforge.common.brewing.BrewingRecipe(input, ingredient, output));
        }

        /**
         * Adds a new brewing recipe with custom logic.
         */
        public void addRecipe(net.neoforged.neoforge.common.brewing.IBrewingRecipe recipe) {
            this.recipes.add(recipe);
        }

        public PotionBrewing build() {
            return new PotionBrewing(List.copyOf(this.containers), List.copyOf(this.potionMixes), List.copyOf(this.containerMixes), List.copyOf(this.recipes));
        }
    }

    static record Mix<T>(Holder<T> from, Ingredient ingredient, Holder<T> to) {
    }
}
