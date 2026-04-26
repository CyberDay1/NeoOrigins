package net.minecraft.world.level.storage.loot.entries;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class EmptyLootItem extends LootPoolSingletonContainer {
    public static final MapCodec<EmptyLootItem> CODEC = RecordCodecBuilder.mapCodec(
        p_299288_ -> singletonFields(p_299288_).apply(p_299288_, EmptyLootItem::new)
    );

    private EmptyLootItem(int p_79519_, int p_79520_, List<LootItemCondition> p_298942_, List<LootItemFunction> p_298310_) {
        super(p_79519_, p_79520_, p_298942_, p_298310_);
    }

    @Override
    public LootPoolEntryType getType() {
        return LootPoolEntries.EMPTY;
    }

    @Override
    public void createItemStack(Consumer<ItemStack> p_79531_, LootContext p_79532_) {
    }

    public static LootPoolSingletonContainer.Builder<?> emptyItem() {
        return simpleBuilder(EmptyLootItem::new);
    }
}
