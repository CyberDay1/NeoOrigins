package net.minecraft.world.level.storage.loot.entries;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class NestedLootTable extends LootPoolSingletonContainer {
    public static final MapCodec<NestedLootTable> CODEC = RecordCodecBuilder.mapCodec(
        p_335330_ -> p_335330_.group(
                    Codec.either(ResourceKey.codec(Registries.LOOT_TABLE), LootTable.DIRECT_CODEC).fieldOf("value").forGetter(p_331842_ -> p_331842_.contents)
                )
                .and(singletonFields(p_335330_))
                .apply(p_335330_, NestedLootTable::new)
    );
    private final Either<ResourceKey<LootTable>, LootTable> contents;

    private NestedLootTable(
        Either<ResourceKey<LootTable>, LootTable> p_330816_, int p_330486_, int p_331616_, List<LootItemCondition> p_330391_, List<LootItemFunction> p_332076_
    ) {
        super(p_330486_, p_331616_, p_330391_, p_332076_);
        this.contents = p_330816_;
    }

    @Override
    public LootPoolEntryType getType() {
        return LootPoolEntries.LOOT_TABLE;
    }

    @Override
    public void createItemStack(Consumer<ItemStack> p_331038_, LootContext p_331648_) {
        this.contents
            .map(
                p_335324_ -> p_331648_.getResolver().get(Registries.LOOT_TABLE, (ResourceKey<LootTable>)p_335324_).map(Holder::value).orElse(LootTable.EMPTY),
                p_330212_ -> (LootTable)p_330212_
            )
            .getRandomItemsRaw(p_331648_, p_331038_);
    }

    @Override
    public void validate(ValidationContext p_330583_) {
        Optional<ResourceKey<LootTable>> optional = this.contents.left();
        if (optional.isPresent()) {
            ResourceKey<LootTable> resourcekey = optional.get();
            if (!p_330583_.allowsReferences()) {
                p_330583_.reportProblem("Uses reference to " + resourcekey.location() + ", but references are not allowed");
                return;
            }

            if (p_330583_.hasVisitedElement(resourcekey)) {
                p_330583_.reportProblem("Table " + resourcekey.location() + " is recursively called");
                return;
            }
        }

        super.validate(p_330583_);
        this.contents
            .ifLeft(
                p_335332_ -> p_330583_.resolver()
                        .get(Registries.LOOT_TABLE, (ResourceKey<LootTable>)p_335332_)
                        .ifPresentOrElse(
                            p_339565_ -> p_339565_.value().validate(p_330583_.enterElement("->{" + p_335332_.location() + "}", (ResourceKey<?>)p_335332_)),
                            () -> p_330583_.reportProblem("Unknown loot table called " + p_335332_.location())
                        )
            )
            .ifRight(p_331183_ -> p_331183_.validate(p_330583_.forChild("->{inline}")));
    }

    public static LootPoolSingletonContainer.Builder<?> lootTableReference(ResourceKey<LootTable> p_335392_) {
        return simpleBuilder(
            (p_331271_, p_331120_, p_331361_, p_331392_) -> new NestedLootTable(Either.left(p_335392_), p_331271_, p_331120_, p_331361_, p_331392_)
        );
    }

    public static LootPoolSingletonContainer.Builder<?> inlineLootTable(LootTable p_330235_) {
        return simpleBuilder(
            (p_330488_, p_330473_, p_330668_, p_331391_) -> new NestedLootTable(Either.right(p_330235_), p_330488_, p_330473_, p_330668_, p_331391_)
        );
    }
}
