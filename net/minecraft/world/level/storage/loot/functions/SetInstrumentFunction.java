package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetInstrumentFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetInstrumentFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_298123_ -> commonFields(p_298123_)
                .and(TagKey.hashedCodec(Registries.INSTRUMENT).fieldOf("options").forGetter(p_298122_ -> p_298122_.options))
                .apply(p_298123_, SetInstrumentFunction::new)
    );
    private final TagKey<Instrument> options;

    private SetInstrumentFunction(List<LootItemCondition> p_298993_, TagKey<Instrument> p_231009_) {
        super(p_298993_);
        this.options = p_231009_;
    }

    @Override
    public LootItemFunctionType<SetInstrumentFunction> getType() {
        return LootItemFunctions.SET_INSTRUMENT;
    }

    @Override
    public ItemStack run(ItemStack p_231017_, LootContext p_231018_) {
        InstrumentItem.setRandom(p_231017_, this.options, p_231018_.getRandom());
        return p_231017_;
    }

    public static LootItemConditionalFunction.Builder<?> setInstrumentOptions(TagKey<Instrument> p_231012_) {
        return simpleBuilder(p_298125_ -> new SetInstrumentFunction(p_298125_, p_231012_));
    }
}
