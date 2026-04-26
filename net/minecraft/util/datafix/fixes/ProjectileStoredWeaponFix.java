package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.minecraft.Util;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class ProjectileStoredWeaponFix extends DataFix {
    public ProjectileStoredWeaponFix(Schema p_345491_) {
        super(p_345491_, true);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ENTITY);
        Type<?> type1 = this.getOutputSchema().getType(References.ENTITY);
        return this.fixTypeEverywhereTyped(
            "Fix Arrow stored weapon",
            type,
            type1,
            ExtraDataFixUtils.chainAllFilters(this.fixChoice("minecraft:arrow"), this.fixChoice("minecraft:spectral_arrow"))
        );
    }

    private Function<Typed<?>, Typed<?>> fixChoice(String p_344880_) {
        Type<?> type = this.getInputSchema().getChoiceType(References.ENTITY, p_344880_);
        Type<?> type1 = this.getOutputSchema().getChoiceType(References.ENTITY, p_344880_);
        return fixChoiceCap(p_344880_, type, type1);
    }

    private static <T> Function<Typed<?>, Typed<?>> fixChoiceCap(String p_345540_, Type<?> p_346235_, Type<T> p_346079_) {
        OpticFinder<?> opticfinder = DSL.namedChoice(p_345540_, p_346235_);
        return p_346386_ -> p_346386_.updateTyped(
                opticfinder, p_346079_, p_345343_ -> Util.writeAndReadTypedOrThrow(p_345343_, p_346079_, UnaryOperator.identity())
            );
    }
}
