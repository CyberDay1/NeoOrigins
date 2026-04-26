package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;

public class V3818_4 extends NamespacedSchema {
    public V3818_4(int p_330615_, Schema p_332049_) {
        super(p_330615_, p_332049_);
    }

    @Override
    public void registerTypes(Schema p_331719_, Map<String, Supplier<TypeTemplate>> p_331233_, Map<String, Supplier<TypeTemplate>> p_330697_) {
        super.registerTypes(p_331719_, p_331233_, p_330697_);
        p_331719_.registerType(
            true,
            References.PARTICLE,
            () -> DSL.optionalFields("item", References.ITEM_STACK.in(p_331719_), "block_state", References.BLOCK_STATE.in(p_331719_))
        );
    }
}
