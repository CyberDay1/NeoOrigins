package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.Map;
import java.util.function.Supplier;

public class V3799 extends NamespacedSchema {
    public V3799(int p_316695_, Schema p_316561_) {
        super(p_316695_, p_316561_);
    }

    @Override
    public Map<String, Supplier<TypeTemplate>> registerEntities(Schema p_316566_) {
        Map<String, Supplier<TypeTemplate>> map = super.registerEntities(p_316566_);
        p_316566_.register(map, "minecraft:armadillo", () -> V100.equipment(p_316566_));
        return map;
    }
}
