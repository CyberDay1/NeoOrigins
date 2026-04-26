package net.minecraft.world.item.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ShapedRecipe implements CraftingRecipe {
    public final ShapedRecipePattern pattern;
    final ItemStack result;
    final String group;
    final CraftingBookCategory category;
    final boolean showNotification;

    public ShapedRecipe(String p_272759_, CraftingBookCategory p_273506_, ShapedRecipePattern p_312827_, ItemStack p_272852_, boolean p_312010_) {
        this.group = p_272759_;
        this.category = p_273506_;
        this.pattern = p_312827_;
        this.result = p_272852_;
        this.showNotification = p_312010_;
    }

    public ShapedRecipe(String p_250221_, CraftingBookCategory p_250716_, ShapedRecipePattern p_312814_, ItemStack p_248581_) {
        this(p_250221_, p_250716_, p_312814_, p_248581_, true);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.SHAPED_RECIPE;
    }

    @Override
    public String getGroup() {
        return this.group;
    }

    @Override
    public CraftingBookCategory category() {
        return this.category;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider p_335668_) {
        return this.result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return this.pattern.ingredients();
    }

    @Override
    public boolean showNotification() {
        return this.showNotification;
    }

    @Override
    public boolean canCraftInDimensions(int p_44161_, int p_44162_) {
        return p_44161_ >= this.pattern.width() && p_44162_ >= this.pattern.height();
    }

    public boolean matches(CraftingInput p_345040_, Level p_44167_) {
        return this.pattern.matches(p_345040_);
    }

    public ItemStack assemble(CraftingInput p_345201_, HolderLookup.Provider p_335688_) {
        return this.getResultItem(p_335688_).copy();
    }

    public int getWidth() {
        return this.pattern.width();
    }

    public int getHeight() {
        return this.pattern.height();
    }

    @Override
    public boolean isIncomplete() {
        NonNullList<Ingredient> nonnulllist = this.getIngredients();
        return nonnulllist.isEmpty() || nonnulllist.stream().filter(p_151277_ -> !p_151277_.isEmpty()).anyMatch(Ingredient::hasNoItems);
    }

    public static class Serializer implements RecipeSerializer<ShapedRecipe> {
        public static final MapCodec<ShapedRecipe> CODEC = RecordCodecBuilder.mapCodec(
            p_340778_ -> p_340778_.group(
                        Codec.STRING.optionalFieldOf("group", "").forGetter(p_311729_ -> p_311729_.group),
                        CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(p_311732_ -> p_311732_.category),
                        ShapedRecipePattern.MAP_CODEC.forGetter(p_311733_ -> p_311733_.pattern),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(p_311730_ -> p_311730_.result),
                        Codec.BOOL.optionalFieldOf("show_notification", Boolean.valueOf(true)).forGetter(p_311731_ -> p_311731_.showNotification)
                    )
                    .apply(p_340778_, ShapedRecipe::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, ShapedRecipe> STREAM_CODEC = StreamCodec.of(
            ShapedRecipe.Serializer::toNetwork, ShapedRecipe.Serializer::fromNetwork
        );

        @Override
        public MapCodec<ShapedRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ShapedRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static ShapedRecipe fromNetwork(RegistryFriendlyByteBuf p_319998_) {
            String s = p_319998_.readUtf();
            CraftingBookCategory craftingbookcategory = p_319998_.readEnum(CraftingBookCategory.class);
            ShapedRecipePattern shapedrecipepattern = ShapedRecipePattern.STREAM_CODEC.decode(p_319998_);
            ItemStack itemstack = ItemStack.STREAM_CODEC.decode(p_319998_);
            boolean flag = p_319998_.readBoolean();
            return new ShapedRecipe(s, craftingbookcategory, shapedrecipepattern, itemstack, flag);
        }

        private static void toNetwork(RegistryFriendlyByteBuf p_320738_, ShapedRecipe p_320586_) {
            p_320738_.writeUtf(p_320586_.group);
            p_320738_.writeEnum(p_320586_.category);
            ShapedRecipePattern.STREAM_CODEC.encode(p_320738_, p_320586_.pattern);
            ItemStack.STREAM_CODEC.encode(p_320738_, p_320586_.result);
            p_320738_.writeBoolean(p_320586_.showNotification);
        }
    }
}
