package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;

public class V3818 extends NamespacedSchema {
    public V3818(int p_330999_, Schema p_330866_) {
        super(p_330999_, p_330866_);
    }

    @Override
    public Map<String, Supplier<TypeTemplate>> registerBlockEntities(Schema p_332109_) {
        Map<String, Supplier<TypeTemplate>> map = super.registerBlockEntities(p_332109_);
        p_332109_.register(
            map, "minecraft:beehive", () -> DSL.optionalFields("bees", DSL.list(DSL.optionalFields("entity_data", References.ENTITY_TREE.in(p_332109_))))
        );
        return map;
    }
}
