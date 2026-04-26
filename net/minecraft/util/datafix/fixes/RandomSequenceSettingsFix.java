package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class RandomSequenceSettingsFix extends DataFix {
    public RandomSequenceSettingsFix(Schema p_294635_) {
        super(p_294635_, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "RandomSequenceSettingsFix",
            this.getInputSchema().getType(References.SAVED_DATA_RANDOM_SEQUENCES),
            p_295522_ -> p_295522_.update(
                    DSL.remainderFinder(), p_295494_ -> p_295494_.update("data", p_296048_ -> p_296048_.emptyMap().set("sequences", p_296048_))
                )
        );
    }
}
