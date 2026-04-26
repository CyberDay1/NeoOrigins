package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;

public class V3808 extends NamespacedSchema {
    public V3808(int p_323738_, Schema p_324346_) {
        super(p_323738_, p_324346_);
    }

    @Override
    public Map<String, Supplier<TypeTemplate>> registerEntities(Schema p_323972_) {
        Map<String, Supplier<TypeTemplate>> map = super.registerEntities(p_323972_);
        p_323972_.register(
            map, "minecraft:horse", p_323604_ -> DSL.optionalFields("SaddleItem", References.ITEM_STACK.in(p_323972_), V100.equipment(p_323972_))
        );
        return map;
    }
}
