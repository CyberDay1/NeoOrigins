package net.minecraft.world.item.trading;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public record ItemCost(Holder<Item> item, int count, DataComponentPredicate components, ItemStack itemStack) {
    public static final Codec<ItemCost> CODEC = RecordCodecBuilder.create(
        p_340786_ -> p_340786_.group(
                    ItemStack.ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(ItemCost::item),
                    ExtraCodecs.POSITIVE_INT.fieldOf("count").orElse(1).forGetter(ItemCost::count),
                    DataComponentPredicate.CODEC.optionalFieldOf("components", DataComponentPredicate.EMPTY).forGetter(ItemCost::components)
                )
                .apply(p_340786_, ItemCost::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemCost> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.holderRegistry(Registries.ITEM),
        ItemCost::item,
        ByteBufCodecs.VAR_INT,
        ItemCost::count,
        DataComponentPredicate.STREAM_CODEC,
        ItemCost::components,
        ItemCost::new
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, Optional<ItemCost>> OPTIONAL_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs::optional);

    public ItemCost(ItemLike p_330939_) {
        this(p_330939_, 1);
    }

    public ItemCost(ItemLike p_332181_, int p_330835_) {
        this(p_332181_.asItem().builtInRegistryHolder(), p_330835_, DataComponentPredicate.EMPTY);
    }

    public ItemCost(Holder<Item> p_330702_, int p_331182_, DataComponentPredicate p_331873_) {
        this(p_330702_, p_331182_, p_331873_, createStack(p_330702_, p_331182_, p_331873_));
    }

    public ItemCost withComponents(UnaryOperator<DataComponentPredicate.Builder> p_331084_) {
        return new ItemCost(this.item, this.count, p_331084_.apply(DataComponentPredicate.builder()).build());
    }

    private static ItemStack createStack(Holder<Item> p_331040_, int p_330313_, DataComponentPredicate p_331395_) {
        return new ItemStack(p_331040_, p_330313_, p_331395_.asPatch());
    }

    public boolean test(ItemStack p_330849_) {
        return p_330849_.is(this.item) && this.components.test((DataComponentHolder)p_330849_);
    }
}
