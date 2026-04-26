package net.minecraft.world.level.storage.loot.entries;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class DynamicLoot extends LootPoolSingletonContainer {
    public static final MapCodec<DynamicLoot> CODEC = RecordCodecBuilder.mapCodec(
        p_298006_ -> p_298006_.group(ResourceLocation.CODEC.fieldOf("name").forGetter(p_298012_ -> p_298012_.name))
                .and(singletonFields(p_298006_))
                .apply(p_298006_, DynamicLoot::new)
    );
    private final ResourceLocation name;

    private DynamicLoot(ResourceLocation p_79465_, int p_79466_, int p_79467_, List<LootItemCondition> p_299033_, List<LootItemFunction> p_298474_) {
        super(p_79466_, p_79467_, p_299033_, p_298474_);
        this.name = p_79465_;
    }

    @Override
    public LootPoolEntryType getType() {
        return LootPoolEntries.DYNAMIC;
    }

    @Override
    public void createItemStack(Consumer<ItemStack> p_79481_, LootContext p_79482_) {
        p_79482_.addDynamicDrops(this.name, p_79481_);
    }

    public static LootPoolSingletonContainer.Builder<?> dynamicEntry(ResourceLocation p_79484_) {
        return simpleBuilder((p_298008_, p_298009_, p_298010_, p_298011_) -> new DynamicLoot(p_79484_, p_298008_, p_298009_, p_298010_, p_298011_));
    }
}
