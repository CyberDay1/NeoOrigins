package net.minecraft.world.level.storage.loot.functions;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Set;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;
import org.slf4j.Logger;

public class SetItemDamageFunction extends LootItemConditionalFunction {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<SetItemDamageFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_298138_ -> commonFields(p_298138_)
                .and(
                    p_298138_.group(
                        NumberProviders.CODEC.fieldOf("damage").forGetter(p_298141_ -> p_298141_.damage),
                        Codec.BOOL.fieldOf("add").orElse(false).forGetter(p_298134_ -> p_298134_.add)
                    )
                )
                .apply(p_298138_, SetItemDamageFunction::new)
    );
    private final NumberProvider damage;
    private final boolean add;

    private SetItemDamageFunction(List<LootItemCondition> p_298394_, NumberProvider p_165428_, boolean p_165429_) {
        super(p_298394_);
        this.damage = p_165428_;
        this.add = p_165429_;
    }

    @Override
    public LootItemFunctionType<SetItemDamageFunction> getType() {
        return LootItemFunctions.SET_DAMAGE;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.damage.getReferencedContextParams();
    }

    @Override
    public ItemStack run(ItemStack p_81048_, LootContext p_81049_) {
        if (p_81048_.isDamageableItem()) {
            int i = p_81048_.getMaxDamage();
            float f = this.add ? 1.0F - (float)p_81048_.getDamageValue() / (float)i : 0.0F;
            float f1 = 1.0F - Mth.clamp(this.damage.getFloat(p_81049_) + f, 0.0F, 1.0F);
            p_81048_.setDamageValue(Mth.floor(f1 * (float)i));
        } else {
            LOGGER.warn("Couldn't set damage of loot item {}", p_81048_);
        }

        return p_81048_;
    }

    public static LootItemConditionalFunction.Builder<?> setDamage(NumberProvider p_165431_) {
        return simpleBuilder(p_298140_ -> new SetItemDamageFunction(p_298140_, p_165431_, false));
    }

    public static LootItemConditionalFunction.Builder<?> setDamage(NumberProvider p_165433_, boolean p_165434_) {
        return simpleBuilder(p_298137_ -> new SetItemDamageFunction(p_298137_, p_165433_, p_165434_));
    }
}
