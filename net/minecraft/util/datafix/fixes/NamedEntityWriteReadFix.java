package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.RewriteResult;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.View;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.functions.PointFreeRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.BitSet;
import net.minecraft.Util;

public abstract class NamedEntityWriteReadFix extends DataFix {
    private final String name;
    private final String entityName;
    private final TypeReference type;

    public NamedEntityWriteReadFix(Schema p_307236_, boolean p_307467_, String p_307246_, TypeReference p_307497_, String p_307636_) {
        super(p_307236_, p_307467_);
        this.name = p_307246_;
        this.type = p_307497_;
        this.entityName = p_307636_;
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(this.type);
        Type<?> type1 = this.getInputSchema().getChoiceType(this.type, this.entityName);
        Type<?> type2 = this.getOutputSchema().getType(this.type);
        Type<?> type3 = this.getOutputSchema().getChoiceType(this.type, this.entityName);
        OpticFinder<?> opticfinder = DSL.namedChoice(this.entityName, type1);
        Type<?> type4 = type1.all(typePatcher(type, type2), true, false).view().newType();
        return this.fix(type, type2, opticfinder, type3, type4);
    }

    private <S, T, A, B> TypeRewriteRule fix(Type<S> p_324369_, Type<T> p_323537_, OpticFinder<A> p_324194_, Type<B> p_324518_, Type<?> p_324108_) {
        return this.fixTypeEverywhere(this.name, p_324369_, p_323537_, p_323223_ -> p_323218_ -> {
                Typed<S> typed = new Typed<>(p_324369_, p_323223_, p_323218_);
                return (T)typed.update(p_324194_, p_324518_, p_323212_ -> {
                    Typed<A> typed1 = new Typed<>((Type<A>)p_324108_, p_323223_, p_323212_);
                    return Util.<A, B>writeAndReadTypedOrThrow(typed1, p_324518_, this::fix).getValue();
                }).getValue();
            });
    }

    private static <A, B> TypeRewriteRule typePatcher(Type<A> p_323821_, Type<B> p_323735_) {
        RewriteResult<A, B> rewriteresult = RewriteResult.create(View.create("Patcher", p_323821_, p_323735_, p_323208_ -> p_323224_ -> {
                throw new UnsupportedOperationException();
            }), new BitSet());
        return TypeRewriteRule.everywhere(TypeRewriteRule.ifSame(p_323821_, rewriteresult), PointFreeRule.nop(), true, true);
    }

    protected abstract <T> Dynamic<T> fix(Dynamic<T> p_307318_);
}
