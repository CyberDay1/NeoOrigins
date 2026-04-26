package net.minecraft.world.level.storage.loot;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.DataResult.Error;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.slf4j.Logger;

public record LootDataType<T>(ResourceKey<Registry<T>> registryKey, Codec<T> codec, LootDataType.Validator<T> validator, @org.jetbrains.annotations.Nullable T defaultValue, Codec<Optional<T>> conditionalCodec, java.util.function.BiConsumer<T, ResourceLocation> idSetter) {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final LootDataType<LootItemCondition> PREDICATE = new LootDataType<>(
        Registries.PREDICATE, LootItemCondition.DIRECT_CODEC, createSimpleValidator()
    );
    public static final LootDataType<LootItemFunction> MODIFIER = new LootDataType<>(
        Registries.ITEM_MODIFIER, LootItemFunctions.ROOT_CODEC, createSimpleValidator()
    );
    public static final LootDataType<LootTable> TABLE = new LootDataType<>(Registries.LOOT_TABLE, LootTable.DIRECT_CODEC, createLootTableValidator(), LootTable.EMPTY, LootTable::setLootTableId);

    /**
     * @deprecated Neo: use the constructor {@link #LootDataType(ResourceKey, Codec, Validator, T, java.util.function.BiConsumer) with a default value and id setter} to support conditions
     */
    @Deprecated
    private LootDataType(ResourceKey<Registry<T>> registryKey, Codec<T> codec, LootDataType.Validator<T> validator) {
        this(registryKey, codec, validator, null, (it, id) -> {});
    }

    private LootDataType(ResourceKey<Registry<T>> registryKey, Codec<T> codec, LootDataType.Validator<T> validator, @org.jetbrains.annotations.Nullable T defaultValue, java.util.function.BiConsumer<T, ResourceLocation> idSetter) {
        this(registryKey, codec, validator, defaultValue, net.neoforged.neoforge.common.conditions.ConditionalOps.createConditionalCodec(codec), idSetter);
    }

    public void runValidation(ValidationContext p_279366_, ResourceKey<T> p_336149_, T p_279124_) {
        this.validator.run(p_279366_, p_336149_, p_279124_);
    }

    public <V> Optional<T> deserialize(ResourceLocation p_279253_, DynamicOps<V> p_324006_, V p_324329_) {
        var dataresult = this.conditionalCodec.parse(p_324006_, p_324329_);
        dataresult.error().ifPresent(p_338121_ -> LOGGER.error("Couldn't parse element {}:{} - {}", this.registryKey, p_279253_, p_338121_.message()));
        return dataresult.result().map(it -> {
            it.ifPresent(val -> idSetter.accept(val, p_279253_));
            T value = it.orElse(defaultValue);
            if (value instanceof LootTable lootTable && p_324006_ instanceof net.minecraft.resources.RegistryOps<V> registryOps) {
                var provider = net.neoforged.neoforge.common.CommonHooks.extractLookupProvider(registryOps);
                value = (T) net.neoforged.neoforge.event.EventHooks.loadLootTable(provider, p_279253_, lootTable);
            }
            return value;
        });
    }

    public static Stream<LootDataType<?>> values() {
        return Stream.of(PREDICATE, MODIFIER, TABLE);
    }

    private static <T extends LootContextUser> LootDataType.Validator<T> createSimpleValidator() {
        return (p_339560_, p_339561_, p_339562_) -> p_339562_.validate(
                p_339560_.enterElement("{" + p_339561_.registry() + "/" + p_339561_.location() + "}", p_339561_)
            );
    }

    private static LootDataType.Validator<LootTable> createLootTableValidator() {
        return (p_279333_, p_279227_, p_279406_) -> {
            p_279406_.validate(
                    p_279333_.setParams(p_279406_.getParamSet()).enterElement("{" + p_279227_.registry() + ":" + p_279227_.location() + "}", p_279227_)
            );
        };
    }

    @FunctionalInterface
    public interface Validator<T> {
        void run(ValidationContext p_279419_, ResourceKey<T> p_335916_, T p_279326_);
    }
}
