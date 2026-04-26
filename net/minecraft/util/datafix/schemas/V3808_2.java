package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;

public class V3808_2 extends NamespacedSchema {
    public V3808_2(int p_342031_, Schema p_342029_) {
        super(p_342031_, p_342029_);
    }

    @Override
    public Map<String, Supplier<TypeTemplate>> registerEntities(Schema p_342035_) {
        Map<String, Supplier<TypeTemplate>> map = super.registerEntities(p_342035_);
        p_342035_.register(
            map,
            "minecraft:trader_llama",
            p_342032_ -> DSL.optionalFields(
                    "Items", DSL.list(References.ITEM_STACK.in(p_342035_)), "SaddleItem", References.ITEM_STACK.in(p_342035_), V100.equipment(p_342035_)
                )
        );
        return map;
    }
}
