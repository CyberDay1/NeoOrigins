package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import com.mojang.datafixers.util.Pair;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;

public class V3818_3 extends NamespacedSchema {
    public V3818_3(int p_332129_, Schema p_330769_) {
        super(p_332129_, p_330769_);
    }

    @Override
    public void registerTypes(Schema p_331415_, Map<String, Supplier<TypeTemplate>> p_331540_, Map<String, Supplier<TypeTemplate>> p_332170_) {
        super.registerTypes(p_331415_, p_331540_, p_332170_);
        p_331415_.registerType(
            true,
            References.DATA_COMPONENTS,
            () -> DSL.optionalFields(
                    Pair.of("minecraft:bees", DSL.list(DSL.optionalFields("entity_data", References.ENTITY_TREE.in(p_331415_)))),
                    Pair.of("minecraft:block_entity_data", References.BLOCK_ENTITY.in(p_331415_)),
                    Pair.of("minecraft:bundle_contents", DSL.list(References.ITEM_STACK.in(p_331415_))),
                    Pair.of(
                        "minecraft:can_break",
                        DSL.optionalFields(
                            "predicates",
                            DSL.list(DSL.optionalFields("blocks", DSL.or(References.BLOCK_NAME.in(p_331415_), DSL.list(References.BLOCK_NAME.in(p_331415_)))))
                        )
                    ),
                    Pair.of(
                        "minecraft:can_place_on",
                        DSL.optionalFields(
                            "predicates",
                            DSL.list(DSL.optionalFields("blocks", DSL.or(References.BLOCK_NAME.in(p_331415_), DSL.list(References.BLOCK_NAME.in(p_331415_)))))
                        )
                    ),
                    Pair.of("minecraft:charged_projectiles", DSL.list(References.ITEM_STACK.in(p_331415_))),
                    Pair.of("minecraft:container", DSL.list(DSL.optionalFields("item", References.ITEM_STACK.in(p_331415_)))),
                    Pair.of("minecraft:entity_data", References.ENTITY_TREE.in(p_331415_)),
                    Pair.of("minecraft:pot_decorations", DSL.list(References.ITEM_NAME.in(p_331415_))),
                    Pair.of("minecraft:food", DSL.optionalFields("using_converts_to", References.ITEM_STACK.in(p_331415_)))
                )
        );
    }
}
