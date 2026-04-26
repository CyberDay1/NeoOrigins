package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Locale;
import java.util.Optional;

public class OptionsLowerCaseLanguageFix extends DataFix {
    public OptionsLowerCaseLanguageFix(Schema p_16659_, boolean p_16660_) {
        super(p_16659_, p_16660_);
    }

    @Override
    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "OptionsLowerCaseLanguageFix", this.getInputSchema().getType(References.OPTIONS), p_16662_ -> p_16662_.update(DSL.remainderFinder(), p_337656_ -> {
                    Optional<String> optional = p_337656_.get("lang").asString().result();
                    return optional.isPresent() ? p_337656_.set("lang", p_337656_.createString(optional.get().toLowerCase(Locale.ROOT))) : p_337656_;
                })
        );
    }
}
