package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;

public class V3682 extends NamespacedSchema {
    public V3682(int p_307476_, Schema p_307428_) {
        super(p_307476_, p_307428_);
    }

    @Override
    public Map<String, Supplier<TypeTemplate>> registerBlockEntities(Schema p_307292_) {
        Map<String, Supplier<TypeTemplate>> map = super.registerBlockEntities(p_307292_);
        p_307292_.register(map, "minecraft:crafter", () -> DSL.optionalFields("Items", DSL.list(References.ITEM_STACK.in(p_307292_))));
        return map;
    }
}
