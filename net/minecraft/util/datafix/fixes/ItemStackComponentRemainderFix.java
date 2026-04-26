package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;

public abstract class ItemStackComponentRemainderFix extends DataFix {
    private final String name;
    private final String componentId;
    private final String newComponentId;

    public ItemStackComponentRemainderFix(Schema p_332692_, String p_332700_, String p_332816_) {
        this(p_332692_, p_332700_, p_332816_, p_332816_);
    }

    public ItemStackComponentRemainderFix(Schema p_332750_, String p_332715_, String p_332770_, String p_332818_) {
        super(p_332750_, false);
        this.name = p_332715_;
        this.componentId = p_332770_;
        this.newComponentId = p_332818_;
    }

    @Override
    public final TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticfinder = type.findField("components");
        return this.fixTypeEverywhereTyped(
            this.name,
            type,
            p_332673_ -> p_332673_.updateTyped(
                    opticfinder,
                    p_332698_ -> p_332698_.update(
                            DSL.remainderFinder(), p_337637_ -> p_337637_.renameAndFixField(this.componentId, this.newComponentId, this::fixComponent)
                        )
                )
        );
    }

    protected abstract <T> Dynamic<T> fixComponent(Dynamic<T> p_332735_);
}
