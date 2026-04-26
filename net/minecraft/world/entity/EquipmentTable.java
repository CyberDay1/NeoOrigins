package net.minecraft.world.entity;

import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

public record EquipmentTable(ResourceKey<LootTable> lootTable, Map<EquipmentSlot, Float> slotDropChances) {
    public static final Codec<Map<EquipmentSlot, Float>> DROP_CHANCES_CODEC = Codec.either(Codec.FLOAT, Codec.unboundedMap(EquipmentSlot.CODEC, Codec.FLOAT))
        .xmap(p_341273_ -> p_341273_.map(EquipmentTable::createForAllSlots, Function.identity()), p_341232_ -> {
            boolean flag = p_341232_.values().stream().distinct().count() == 1L;
            boolean flag1 = p_341232_.keySet().containsAll(Arrays.asList(EquipmentSlot.values()));
            return flag && flag1 ? Either.left(p_341232_.values().stream().findFirst().orElse(0.0F)) : Either.right((Map<EquipmentSlot, Float>)p_341232_);
        });
    public static final Codec<EquipmentTable> CODEC = RecordCodecBuilder.create(
        p_341275_ -> p_341275_.group(
                    ResourceKey.codec(Registries.LOOT_TABLE).fieldOf("loot_table").forGetter(EquipmentTable::lootTable),
                    DROP_CHANCES_CODEC.optionalFieldOf("slot_drop_chances", Map.of()).forGetter(EquipmentTable::slotDropChances)
                )
                .apply(p_341275_, EquipmentTable::new)
    );

    private static Map<EquipmentSlot, Float> createForAllSlots(float p_340946_) {
        return createForAllSlots(List.of(EquipmentSlot.values()), p_340946_);
    }

    private static Map<EquipmentSlot, Float> createForAllSlots(List<EquipmentSlot> p_340910_, float p_341369_) {
        Map<EquipmentSlot, Float> map = Maps.newHashMap();

        for (EquipmentSlot equipmentslot : p_340910_) {
            map.put(equipmentslot, p_341369_);
        }

        return map;
    }
}
