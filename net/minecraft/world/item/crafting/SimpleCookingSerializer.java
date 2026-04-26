package net.minecraft.world.item.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public class SimpleCookingSerializer<T extends AbstractCookingRecipe> implements RecipeSerializer<T> {
    private final AbstractCookingRecipe.Factory<T> factory;
    private final MapCodec<T> codec;
    private final StreamCodec<RegistryFriendlyByteBuf, T> streamCodec;

    public SimpleCookingSerializer(AbstractCookingRecipe.Factory<T> p_312065_, int p_44331_) {
        this.factory = p_312065_;
        this.codec = RecordCodecBuilder.mapCodec(
            p_300831_ -> p_300831_.group(
                        Codec.STRING.optionalFieldOf("group", "").forGetter(p_300832_ -> p_300832_.group),
                        CookingBookCategory.CODEC.fieldOf("category").orElse(CookingBookCategory.MISC).forGetter(p_300828_ -> p_300828_.category),
                        Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(p_300833_ -> p_300833_.ingredient),
                        ItemStack.CODEC.fieldOf("result").forGetter(p_300827_ -> p_300827_.result),
                        Codec.FLOAT.fieldOf("experience").orElse(0.0F).forGetter(p_300826_ -> p_300826_.experience),
                        Codec.INT.fieldOf("cookingtime").orElse(p_44331_).forGetter(p_300834_ -> p_300834_.cookingTime)
                    )
                    .apply(p_300831_, p_312065_::create)
        );
        this.streamCodec = StreamCodec.of(this::toNetwork, this::fromNetwork);
    }

    @Override
    public MapCodec<T> codec() {
        return this.codec;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() {
        return this.streamCodec;
    }

    private T fromNetwork(RegistryFriendlyByteBuf p_320282_) {
        String s = p_320282_.readUtf();
        CookingBookCategory cookingbookcategory = p_320282_.readEnum(CookingBookCategory.class);
        Ingredient ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(p_320282_);
        ItemStack itemstack = ItemStack.STREAM_CODEC.decode(p_320282_);
        float f = p_320282_.readFloat();
        int i = p_320282_.readVarInt();
        return this.factory.create(s, cookingbookcategory, ingredient, itemstack, f, i);
    }

    private void toNetwork(RegistryFriendlyByteBuf p_320422_, T p_320933_) {
        p_320422_.writeUtf(p_320933_.group);
        p_320422_.writeEnum(p_320933_.category());
        Ingredient.CONTENTS_STREAM_CODEC.encode(p_320422_, p_320933_.ingredient);
        ItemStack.STREAM_CODEC.encode(p_320422_, p_320933_.result);
        p_320422_.writeFloat(p_320933_.experience);
        p_320422_.writeVarInt(p_320933_.cookingTime);
    }

    public AbstractCookingRecipe create(
        String p_312671_, CookingBookCategory p_312067_, Ingredient p_312327_, ItemStack p_311758_, float p_312386_, int p_311986_
    ) {
        return this.factory.create(p_312671_, p_312067_, p_312327_, p_311758_, p_312386_, p_311986_);
    }
}
