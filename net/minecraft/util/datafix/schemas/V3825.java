package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;

public class V3825 extends NamespacedSchema {
    public V3825(int p_338191_, Schema p_338700_) {
        super(p_338191_, p_338700_);
    }

    @Override
    public Map<String, Supplier<TypeTemplate>> registerEntities(Schema p_338861_) {
        Map<String, Supplier<TypeTemplate>> map = super.registerEntities(p_338861_);
        p_338861_.register(map, "minecraft:ominous_item_spawner", () -> DSL.optionalFields("item", References.ITEM_STACK.in(p_338861_)));
        return map;
    }
}
