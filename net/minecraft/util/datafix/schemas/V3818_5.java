package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;

public class V3818_5 extends NamespacedSchema {
    public V3818_5(int p_341021_, Schema p_340906_) {
        super(p_341021_, p_340906_);
    }

    @Override
    public void registerTypes(Schema p_340978_, Map<String, Supplier<TypeTemplate>> p_341073_, Map<String, Supplier<TypeTemplate>> p_341295_) {
        super.registerTypes(p_340978_, p_341073_, p_341295_);
        p_340978_.registerType(
            true,
            References.ITEM_STACK,
            () -> DSL.optionalFields("id", References.ITEM_NAME.in(p_340978_), "components", References.DATA_COMPONENTS.in(p_340978_))
        );
    }
}
