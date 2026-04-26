package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;

public class V3689 extends NamespacedSchema {
    public V3689(int p_311940_, Schema p_312348_) {
        super(p_311940_, p_312348_);
    }

    @Override
    public Map<String, Supplier<TypeTemplate>> registerEntities(Schema p_312332_) {
        Map<String, Supplier<TypeTemplate>> map = super.registerEntities(p_312332_);
        p_312332_.register(map, "minecraft:breeze", () -> V100.equipment(p_312332_));
        p_312332_.registerSimple(map, "minecraft:wind_charge");
        p_312332_.registerSimple(map, "minecraft:breeze_wind_charge");
        return map;
    }

    @Override
    public Map<String, Supplier<TypeTemplate>> registerBlockEntities(Schema p_312226_) {
        Map<String, Supplier<TypeTemplate>> map = super.registerBlockEntities(p_312226_);
        p_312226_.register(
            map,
            "minecraft:trial_spawner",
            () -> DSL.optionalFields(
                    "spawn_potentials",
                    DSL.list(DSL.fields("data", DSL.fields("entity", References.ENTITY_TREE.in(p_312226_)))),
                    "spawn_data",
                    DSL.fields("entity", References.ENTITY_TREE.in(p_312226_))
                )
        );
        return map;
    }
}
