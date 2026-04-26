package net.minecraft.world.level.storage.loot.entries;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class LootItem extends LootPoolSingletonContainer {
    public static final MapCodec<LootItem> CODEC = RecordCodecBuilder.mapCodec(
        p_344670_ -> p_344670_.group(BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("name").forGetter(p_298016_ -> p_298016_.item))
                .and(singletonFields(p_344670_))
                .apply(p_344670_, LootItem::new)
    );
    private final Holder<Item> item;

    private LootItem(Holder<Item> p_298423_, int p_79567_, int p_79568_, List<LootItemCondition> p_299249_, List<LootItemFunction> p_299128_) {
        super(p_79567_, p_79568_, p_299249_, p_299128_);
        this.item = p_298423_;
    }

    @Override
    public LootPoolEntryType getType() {
        return LootPoolEntries.ITEM;
    }

    @Override
    public void createItemStack(Consumer<ItemStack> p_79590_, LootContext p_79591_) {
        p_79590_.accept(new ItemStack(this.item));
    }

    public static LootPoolSingletonContainer.Builder<?> lootTableItem(ItemLike p_79580_) {
        return simpleBuilder(
            (p_298018_, p_298019_, p_298020_, p_298021_) -> new LootItem(p_79580_.asItem().builtInRegistryHolder(), p_298018_, p_298019_, p_298020_, p_298021_)
        );
    }
}
