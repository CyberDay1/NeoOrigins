package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;

public class V3808_1 extends NamespacedSchema {
    public V3808_1(int p_324534_, Schema p_323792_) {
        super(p_324534_, p_323792_);
    }

    @Override
    public Map<String, Supplier<TypeTemplate>> registerEntities(Schema p_323730_) {
        Map<String, Supplier<TypeTemplate>> map = super.registerEntities(p_323730_);
        p_323730_.register(
            map,
            "minecraft:llama",
            p_324060_ -> DSL.optionalFields(
                    "Items", DSL.list(References.ITEM_STACK.in(p_323730_)), "SaddleItem", References.ITEM_STACK.in(p_323730_), V100.equipment(p_323730_)
                )
        );
        return map;
    }
}
