package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.armortrim.TrimMaterial;
import net.minecraft.world.item.armortrim.TrimMaterials;
import net.minecraft.world.item.armortrim.TrimPattern;
import net.minecraft.world.item.armortrim.TrimPatterns;
import net.minecraft.world.level.Level;

public class SmithingTrimRecipe implements SmithingRecipe {
    final Ingredient template;
    final Ingredient base;
    final Ingredient addition;

    public SmithingTrimRecipe(Ingredient p_267298_, Ingredient p_266862_, Ingredient p_267050_) {
        this.template = p_267298_;
        this.base = p_266862_;
        this.addition = p_267050_;
    }

    public boolean matches(SmithingRecipeInput p_346082_, Level p_345460_) {
        return this.template.test(p_346082_.template()) && this.base.test(p_346082_.base()) && this.addition.test(p_346082_.addition());
    }

    public ItemStack assemble(SmithingRecipeInput p_345750_, HolderLookup.Provider p_335536_) {
        ItemStack itemstack = p_345750_.base();
        if (this.base.test(itemstack)) {
            Optional<Holder.Reference<TrimMaterial>> optional = TrimMaterials.getFromIngredient(p_335536_, p_345750_.addition());
            Optional<Holder.Reference<TrimPattern>> optional1 = TrimPatterns.getFromTemplate(p_335536_, p_345750_.template());
            if (optional.isPresent() && optional1.isPresent()) {
                ArmorTrim armortrim = itemstack.get(DataComponents.TRIM);
                if (armortrim != null && armortrim.hasPatternAndMaterial(optional1.get(), optional.get())) {
                    return ItemStack.EMPTY;
                }

                ItemStack itemstack1 = itemstack.copyWithCount(1);
                itemstack1.set(DataComponents.TRIM, new ArmorTrim(optional.get(), optional1.get()));
                return itemstack1;
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider p_335445_) {
        ItemStack itemstack = new ItemStack(Items.IRON_CHESTPLATE);
        Optional<Holder.Reference<TrimPattern>> optional = p_335445_.lookupOrThrow(Registries.TRIM_PATTERN).listElements().findFirst();
        Optional<Holder.Reference<TrimMaterial>> optional1 = p_335445_.lookupOrThrow(Registries.TRIM_MATERIAL).get(TrimMaterials.REDSTONE);
        if (optional.isPresent() && optional1.isPresent()) {
            itemstack.set(DataComponents.TRIM, new ArmorTrim(optional1.get(), optional.get()));
        }

        return itemstack;
    }

    @Override
    public boolean isTemplateIngredient(ItemStack p_266762_) {
        return this.template.test(p_266762_);
    }

    @Override
    public boolean isBaseIngredient(ItemStack p_266795_) {
        return this.base.test(p_266795_);
    }

    @Override
    public boolean isAdditionIngredient(ItemStack p_266922_) {
        return this.addition.test(p_266922_);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.SMITHING_TRIM;
    }

    @Override
    public boolean isIncomplete() {
        return Stream.of(this.template, this.base, this.addition).anyMatch(Ingredient::hasNoItems);
    }

    public static class Serializer implements RecipeSerializer<SmithingTrimRecipe> {
        private static final MapCodec<SmithingTrimRecipe> CODEC = RecordCodecBuilder.mapCodec(
            p_301227_ -> p_301227_.group(
                        Ingredient.CODEC.fieldOf("template").forGetter(p_301070_ -> p_301070_.template),
                        Ingredient.CODEC.fieldOf("base").forGetter(p_300969_ -> p_300969_.base),
                        Ingredient.CODEC.fieldOf("addition").forGetter(p_300977_ -> p_300977_.addition)
                    )
                    .apply(p_301227_, SmithingTrimRecipe::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, SmithingTrimRecipe> STREAM_CODEC = StreamCodec.of(
            SmithingTrimRecipe.Serializer::toNetwork, SmithingTrimRecipe.Serializer::fromNetwork
        );

        @Override
        public MapCodec<SmithingTrimRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SmithingTrimRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static SmithingTrimRecipe fromNetwork(RegistryFriendlyByteBuf p_320719_) {
            Ingredient ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(p_320719_);
            Ingredient ingredient1 = Ingredient.CONTENTS_STREAM_CODEC.decode(p_320719_);
            Ingredient ingredient2 = Ingredient.CONTENTS_STREAM_CODEC.decode(p_320719_);
            return new SmithingTrimRecipe(ingredient, ingredient1, ingredient2);
        }

        private static void toNetwork(RegistryFriendlyByteBuf p_319922_, SmithingTrimRecipe p_320655_) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(p_319922_, p_320655_.template);
            Ingredient.CONTENTS_STREAM_CODEC.encode(p_319922_, p_320655_.base);
            Ingredient.CONTENTS_STREAM_CODEC.encode(p_319922_, p_320655_.addition);
        }
    }
}
