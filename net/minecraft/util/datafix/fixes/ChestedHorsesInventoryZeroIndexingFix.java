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

public class ChestedHorsesInventoryZeroIndexingFix extends DataFix {
    public ChestedHorsesInventoryZeroIndexingFix(Schema p_324298_) {
        super(p_324298_, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        OpticFinder<Pair<String, Pair<Either<Pair<String, String>, Unit>, Pair<Either<?, Unit>, Dynamic<?>>>>> opticfinder = DSL.typeFinder(
            (Type<Pair<String, Pair<Either<Pair<String, String>, Unit>, Pair<Either<?, Unit>, Dynamic<?>>>>>)this.getInputSchema()
                .getType(References.ITEM_STACK)
        );
        Type<?> type = this.getInputSchema().getType(References.ENTITY);
        return TypeRewriteRule.seq(
            this.horseLikeInventoryIndexingFixer(opticfinder, type, "minecraft:llama"),
            this.horseLikeInventoryIndexingFixer(opticfinder, type, "minecraft:trader_llama"),
            this.horseLikeInventoryIndexingFixer(opticfinder, type, "minecraft:mule"),
            this.horseLikeInventoryIndexingFixer(opticfinder, type, "minecraft:donkey")
        );
    }

    private TypeRewriteRule horseLikeInventoryIndexingFixer(
        OpticFinder<Pair<String, Pair<Either<Pair<String, String>, Unit>, Pair<Either<?, Unit>, Dynamic<?>>>>> p_324299_, Type<?> p_323519_, String p_324132_
    ) {
        Type<?> type = this.getInputSchema().getChoiceType(References.ENTITY, p_324132_);
        OpticFinder<?> opticfinder = DSL.namedChoice(p_324132_, type);
        OpticFinder<?> opticfinder1 = type.findField("Items");
        return this.fixTypeEverywhereTyped(
            "Fix non-zero indexing in chest horse type " + p_324132_,
            p_323519_,
            p_324100_ -> p_324100_.updateTyped(
                    opticfinder,
                    p_323809_ -> p_323809_.updateTyped(
                            opticfinder1,
                            p_324159_ -> p_324159_.update(
                                    p_324299_,
                                    p_323642_ -> p_323642_.mapSecond(
                                            p_324444_ -> p_324444_.mapSecond(
                                                    p_323677_ -> p_323677_.mapSecond(
                                                            p_323925_ -> p_323925_.update(
                                                                    "Slot", p_324395_ -> p_324395_.createByte((byte)(p_324395_.asInt(2) - 2))
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }
}
