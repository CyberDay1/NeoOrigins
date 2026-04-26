package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SmithingTransformRecipe implements SmithingRecipe {
    final Ingredient template;
    final Ingredient base;
    final Ingredient addition;
    final ItemStack result;

    public SmithingTransformRecipe(Ingredient p_266750_, Ingredient p_266787_, Ingredient p_267292_, ItemStack p_267031_) {
        this.template = p_266750_;
        this.base = p_266787_;
        this.addition = p_267292_;
        this.result = p_267031_;
    }

    public boolean matches(SmithingRecipeInput p_346359_, Level p_266781_) {
        return this.template.test(p_346359_.template()) && this.base.test(p_346359_.base()) && this.addition.test(p_346359_.addition());
    }

    public ItemStack assemble(SmithingRecipeInput p_345093_, HolderLookup.Provider p_345488_) {
        ItemStack itemstack = p_345093_.base().transmuteCopy(this.result.getItem(), this.result.getCount());
        itemstack.applyComponents(this.result.getComponentsPatch());
        return itemstack;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider p_335712_) {
        return this.result;
    }

    @Override
    public boolean isTemplateIngredient(ItemStack p_267113_) {
        return this.template.test(p_267113_);
    }

    @Override
    public boolean isBaseIngredient(ItemStack p_267276_) {
        return this.base.test(p_267276_);
    }

    @Override
    public boolean isAdditionIngredient(ItemStack p_267260_) {
        return this.addition.test(p_267260_);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.SMITHING_TRANSFORM;
    }

    @Override
    public boolean isIncomplete() {
        return Stream.of(this.template, this.base, this.addition).anyMatch(Ingredient::hasNoItems);
    }

    public static class Serializer implements RecipeSerializer<SmithingTransformRecipe> {
        private static final MapCodec<SmithingTransformRecipe> CODEC = RecordCodecBuilder.mapCodec(
            p_340782_ -> p_340782_.group(
                        Ingredient.CODEC.fieldOf("template").forGetter(p_301310_ -> p_301310_.template),
                        Ingredient.CODEC.fieldOf("base").forGetter(p_300938_ -> p_300938_.base),
                        Ingredient.CODEC.fieldOf("addition").forGetter(p_301153_ -> p_301153_.addition),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(p_300935_ -> p_300935_.result)
                    )
                    .apply(p_340782_, SmithingTransformRecipe::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, SmithingTransformRecipe> STREAM_CODEC = StreamCodec.of(
            SmithingTransformRecipe.Serializer::toNetwork, SmithingTransformRecipe.Serializer::fromNetwork
        );

        @Override
        public MapCodec<SmithingTransformRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SmithingTransformRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static SmithingTransformRecipe fromNetwork(RegistryFriendlyByteBuf p_320375_) {
            Ingredient ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(p_320375_);
            Ingredient ingredient1 = Ingredient.CONTENTS_STREAM_CODEC.decode(p_320375_);
            Ingredient ingredient2 = Ingredient.CONTENTS_STREAM_CODEC.decode(p_320375_);
            ItemStack itemstack = ItemStack.STREAM_CODEC.decode(p_320375_);
            return new SmithingTransformRecipe(ingredient, ingredient1, ingredient2, itemstack);
        }

        private static void toNetwork(RegistryFriendlyByteBuf p_320743_, SmithingTransformRecipe p_319840_) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(p_320743_, p_319840_.template);
            Ingredient.CONTENTS_STREAM_CODEC.encode(p_320743_, p_319840_.base);
            Ingredient.CONTENTS_STREAM_CODEC.encode(p_320743_, p_319840_.addition);
            ItemStack.STREAM_CODEC.encode(p_320743_, p_319840_.result);
        }
    }
}
