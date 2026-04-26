package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.Map;
import java.util.function.Supplier;

public class V3816 extends NamespacedSchema {
    public V3816(int p_326878_, Schema p_326785_) {
        super(p_326878_, p_326785_);
    }

    @Override
    public Map<String, Supplier<TypeTemplate>> registerEntities(Schema p_326810_) {
        Map<String, Supplier<TypeTemplate>> map = super.registerEntities(p_326810_);
        p_326810_.register(map, "minecraft:bogged", () -> V100.equipment(p_326810_));
        return map;
    }
}
