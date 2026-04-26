package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class EmptyItemInVillagerTradeFix extends DataFix {
    public EmptyItemInVillagerTradeFix(Schema p_340827_) {
        super(p_340827_, false);
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.VILLAGER_TRADE);
        return this.writeFixAndRead("EmptyItemInVillagerTradeFix", type, type, p_342001_ -> {
            Dynamic<?> dynamic = p_342001_.get("buyB").orElseEmptyMap();
            String s = NamespacedSchema.ensureNamespaced(dynamic.get("id").asString("minecraft:air"));
            int i = dynamic.get("count").asInt(0);
            return !s.equals("minecraft:air") && i != 0 ? p_342001_ : p_342001_.remove("buyB");
        });
    }
}
