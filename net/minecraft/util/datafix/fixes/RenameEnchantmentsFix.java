package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DataResult.Error;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class RenameEnchantmentsFix extends DataFix {
    final String name;
    final Map<String, String> renames;

    public RenameEnchantmentsFix(Schema p_320301_, String p_320802_, Map<String, String> p_320622_) {
        super(p_320301_, false);
        this.name = p_320802_;
        this.renames = p_320622_;
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticfinder = type.findField("tag");
        return this.fixTypeEverywhereTyped(
            this.name, type, p_320338_ -> p_320338_.updateTyped(opticfinder, p_320499_ -> p_320499_.update(DSL.remainderFinder(), this::fixTag))
        );
    }

    private Dynamic<?> fixTag(Dynamic<?> p_320665_) {
        p_320665_ = this.fixEnchantmentList(p_320665_, "Enchantments");
        return this.fixEnchantmentList(p_320665_, "StoredEnchantments");
    }

    private Dynamic<?> fixEnchantmentList(Dynamic<?> p_320427_, String p_319969_) {
        return p_320427_.update(
            p_319969_,
            p_337664_ -> p_337664_.asStreamOpt()
                    .map(
                        p_320850_ -> p_320850_.map(
                                p_320794_ -> p_320794_.update(
                                        "id",
                                        p_337663_ -> p_337663_.asString()
                                                .map(
                                                    p_344279_ -> p_320794_.createString(
                                                            this.renames.getOrDefault(NamespacedSchema.ensureNamespaced(p_344279_), p_344279_)
                                                        )
                                                )
                                                .mapOrElse(Function.identity(), p_338509_ -> p_337663_)
                                    )
                            )
                    )
                    .map(p_337664_::createList)
                    .mapOrElse(Function.identity(), p_338319_ -> p_337664_)
        );
    }
}
