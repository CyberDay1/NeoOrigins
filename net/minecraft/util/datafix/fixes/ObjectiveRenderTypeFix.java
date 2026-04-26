package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class ObjectiveRenderTypeFix extends DataFix {
    public ObjectiveRenderTypeFix(Schema p_16536_, boolean p_16537_) {
        super(p_16536_, p_16537_);
    }

    private static String getRenderType(String p_262957_) {
        return p_262957_.equals("health") ? "hearts" : "integer";
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.OBJECTIVE);
        return this.fixTypeEverywhereTyped("ObjectiveRenderTypeFix", type, p_181041_ -> p_181041_.update(DSL.remainderFinder(), p_337651_ -> {
                Optional<String> optional = p_337651_.get("RenderType").asString().result();
                if (optional.isEmpty()) {
                    String s = p_337651_.get("CriteriaName").asString("");
                    String s1 = getRenderType(s);
                    return p_337651_.set("RenderType", p_337651_.createString(s1));
                } else {
                    return p_337651_;
                }
            }));
    }
}
