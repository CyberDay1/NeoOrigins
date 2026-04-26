package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public abstract class ItemStackTagFix extends DataFix {
    private final String name;
    private final Predicate<String> idFilter;

    public ItemStackTagFix(Schema p_216682_, String p_216683_, Predicate<String> p_216684_) {
        super(p_216682_, false);
        this.name = p_216683_;
        this.idFilter = p_216684_;
    }

    @Override
    public final TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        return this.fixTypeEverywhereTyped(this.name, type, createFixer(type, this.idFilter, this::fixItemStackTag));
    }

    public static UnaryOperator<Typed<?>> createFixer(Type<?> p_326099_, Predicate<String> p_326164_, UnaryOperator<Dynamic<?>> p_326258_) {
        OpticFinder<Pair<String, String>> opticfinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        OpticFinder<?> opticfinder1 = p_326099_.findField("tag");
        return p_325658_ -> {
            Optional<Pair<String, String>> optional = p_325658_.getOptional(opticfinder);
            return optional.isPresent() && p_326164_.test(optional.get().getSecond())
                ? p_325658_.updateTyped(opticfinder1, p_325653_ -> p_325653_.update(DSL.remainderFinder(), p_326258_))
                : p_325658_;
        };
    }

    protected abstract <T> Dynamic<T> fixItemStackTag(Dynamic<T> p_216691_);
}
