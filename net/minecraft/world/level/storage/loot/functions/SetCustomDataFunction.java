package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetCustomDataFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetCustomDataFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_340803_ -> commonFields(p_340803_)
                .and(TagParser.LENIENT_CODEC.fieldOf("tag").forGetter(p_330759_ -> p_330759_.tag))
                .apply(p_340803_, SetCustomDataFunction::new)
    );
    private final CompoundTag tag;

    private SetCustomDataFunction(List<LootItemCondition> p_330259_, CompoundTag p_330792_) {
        super(p_330259_);
        this.tag = p_330792_;
    }

    @Override
    public LootItemFunctionType<SetCustomDataFunction> getType() {
        return LootItemFunctions.SET_CUSTOM_DATA;
    }

    @Override
    public ItemStack run(ItemStack p_330737_, LootContext p_331310_) {
        CustomData.update(DataComponents.CUSTOM_DATA, p_330737_, p_331655_ -> p_331655_.merge(this.tag));
        return p_330737_;
    }

    @Deprecated
    public static LootItemConditionalFunction.Builder<?> setCustomData(CompoundTag p_330248_) {
        return simpleBuilder(p_330691_ -> new SetCustomDataFunction(p_330691_, p_330248_));
    }
}
