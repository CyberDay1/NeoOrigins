package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetPotionFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetPotionFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_348460_ -> commonFields(p_348460_).and(Potion.CODEC.fieldOf("id").forGetter(p_298158_ -> p_298158_.potion)).apply(p_348460_, SetPotionFunction::new)
    );
    private final Holder<Potion> potion;

    private SetPotionFunction(List<LootItemCondition> p_299010_, Holder<Potion> p_298587_) {
        super(p_299010_);
        this.potion = p_298587_;
    }

    @Override
    public LootItemFunctionType<SetPotionFunction> getType() {
        return LootItemFunctions.SET_POTION;
    }

    @Override
    public ItemStack run(ItemStack p_193073_, LootContext p_193074_) {
        p_193073_.update(DataComponents.POTION_CONTENTS, PotionContents.EMPTY, this.potion, PotionContents::withPotion);
        return p_193073_;
    }

    public static LootItemConditionalFunction.Builder<?> setPotion(Holder<Potion> p_316540_) {
        return simpleBuilder(p_316108_ -> new SetPotionFunction(p_316108_, p_316540_));
    }
}
