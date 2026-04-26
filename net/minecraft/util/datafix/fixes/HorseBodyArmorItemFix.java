package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Streams;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class HorseBodyArmorItemFix extends NamedEntityWriteReadFix {
    private final String previousBodyArmorTag;
    private final boolean clearArmorItems;

    public HorseBodyArmorItemFix(Schema p_323857_, String p_324552_, String p_324464_, boolean p_342009_) {
        super(p_323857_, true, "Horse armor fix for " + p_324552_, References.ENTITY, p_324552_);
        this.previousBodyArmorTag = p_324464_;
        this.clearArmorItems = p_342009_;
    }

    @Override
    protected <T> Dynamic<T> fix(Dynamic<T> p_324321_) {
        Optional<? extends Dynamic<?>> optional = p_324321_.get(this.previousBodyArmorTag).result();
        if (optional.isPresent()) {
            Dynamic<?> dynamic = (Dynamic<?>)optional.get();
            Dynamic<T> dynamic1 = p_324321_.remove(this.previousBodyArmorTag);
            if (this.clearArmorItems) {
                dynamic1 = dynamic1.update(
                    "ArmorItems",
                    p_342004_ -> p_342004_.createList(
                            Streams.mapWithIndex(p_342004_.asStream(), (p_342005_, p_342002_) -> p_342002_ == 2L ? p_342005_.emptyMap() : p_342005_)
                        )
                );
                dynamic1 = dynamic1.update(
                    "ArmorDropChances",
                    p_342012_ -> p_342012_.createList(
                            Streams.mapWithIndex(p_342012_.asStream(), (p_342011_, p_342007_) -> p_342007_ == 2L ? p_342011_.createFloat(0.085F) : p_342011_)
                        )
                );
            }

            dynamic1 = dynamic1.set("body_armor_item", dynamic);
            return dynamic1.set("body_armor_drop_chance", p_324321_.createFloat(2.0F));
        } else {
            return p_324321_;
        }
    }
}
