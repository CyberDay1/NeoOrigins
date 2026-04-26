package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;

public class V3683 extends NamespacedSchema {
    public V3683(int p_307268_, Schema p_307218_) {
        super(p_307268_, p_307218_);
    }

    @Override
    public Map<String, Supplier<TypeTemplate>> registerEntities(Schema p_307575_) {
        Map<String, Supplier<TypeTemplate>> map = super.registerEntities(p_307575_);
        p_307575_.register(map, "minecraft:tnt", () -> DSL.optionalFields("block_state", References.BLOCK_STATE.in(p_307575_)));
        return map;
    }
}
