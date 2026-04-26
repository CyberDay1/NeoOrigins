package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;

public class V3685 extends NamespacedSchema {
    public V3685(int p_308968_, Schema p_309144_) {
        super(p_308968_, p_309144_);
    }

    protected static TypeTemplate abstractArrow(Schema p_309110_) {
        return DSL.optionalFields("inBlockState", References.BLOCK_STATE.in(p_309110_), "item", References.ITEM_STACK.in(p_309110_));
    }

    @Override
    public Map<String, Supplier<TypeTemplate>> registerEntities(Schema p_309032_) {
        Map<String, Supplier<TypeTemplate>> map = super.registerEntities(p_309032_);
        p_309032_.register(map, "minecraft:trident", () -> abstractArrow(p_309032_));
        p_309032_.register(map, "minecraft:spectral_arrow", () -> abstractArrow(p_309032_));
        p_309032_.register(map, "minecraft:arrow", () -> abstractArrow(p_309032_));
        return map;
    }
}
