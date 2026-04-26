package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class EmptyItemInHotbarFix extends DataFix {
    public EmptyItemInHotbarFix(Schema p_331137_) {
        super(p_331137_, false);
    }

    @Override
    public TypeRewriteRule makeRule() {
        OpticFinder<Pair<String, Pair<Either<Pair<String, String>, Unit>, Pair<Either<?, Unit>, Dynamic<?>>>>> opticfinder = DSL.typeFinder(
            (Type<Pair<String, Pair<Either<Pair<String, String>, Unit>, Pair<Either<?, Unit>, Dynamic<?>>>>>)this.getInputSchema()
                .getType(References.ITEM_STACK)
        );
        return this.fixTypeEverywhereTyped(
            "EmptyItemInHotbarFix",
            this.getInputSchema().getType(References.HOTBAR),
            p_331874_ -> p_331874_.update(opticfinder, p_331516_ -> p_331516_.mapSecond(p_331944_ -> {
                        Optional<String> optional = p_331944_.getFirst().left().map(Pair::getSecond);
                        Dynamic<?> dynamic = p_331944_.getSecond().getSecond();
                        boolean flag = optional.isEmpty() || optional.get().equals("minecraft:air");
                        boolean flag1 = dynamic.get("Count").asInt(0) <= 0;
                        return !flag && !flag1 ? p_331944_ : Pair.of(Either.right(Unit.INSTANCE), Pair.of(Either.right(Unit.INSTANCE), dynamic.emptyMap()));
                    }))
        );
    }
}
