package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class PlayerHeadBlockProfileFix extends NamedEntityFix {
    public PlayerHeadBlockProfileFix(Schema p_332754_) {
        super(p_332754_, false, "PlayerHeadBlockProfileFix", References.BLOCK_ENTITY, "minecraft:skull");
    }

    @Override
    protected Typed<?> fix(Typed<?> p_332765_) {
        return p_332765_.update(DSL.remainderFinder(), this::fix);
    }

    private <T> Dynamic<T> fix(Dynamic<T> p_332648_) {
        Optional<Dynamic<T>> optional = p_332648_.get("SkullOwner").result();
        Optional<Dynamic<T>> optional1 = p_332648_.get("ExtraType").result();
        Optional<Dynamic<T>> optional2 = optional.or(() -> optional1);
        if (optional2.isEmpty()) {
            return p_332648_;
        } else {
            p_332648_ = p_332648_.remove("SkullOwner").remove("ExtraType");
            return p_332648_.set("profile", ItemStackComponentizationFix.fixProfile(optional2.get()));
        }
    }
}
