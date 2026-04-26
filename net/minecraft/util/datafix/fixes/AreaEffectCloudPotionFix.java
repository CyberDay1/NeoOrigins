package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class AreaEffectCloudPotionFix extends NamedEntityFix {
    public AreaEffectCloudPotionFix(Schema p_331376_) {
        super(p_331376_, false, "AreaEffectCloudPotionFix", References.ENTITY, "minecraft:area_effect_cloud");
    }

    @Override
    protected Typed<?> fix(Typed<?> p_330255_) {
        return p_330255_.update(DSL.remainderFinder(), this::fix);
    }

    private <T> Dynamic<T> fix(Dynamic<T> p_330468_) {
        Optional<Dynamic<T>> optional = p_330468_.get("Color").result();
        Optional<Dynamic<T>> optional1 = p_330468_.get("effects").result();
        Optional<Dynamic<T>> optional2 = p_330468_.get("Potion").result();
        p_330468_ = p_330468_.remove("Color").remove("effects").remove("Potion");
        if (optional.isEmpty() && optional1.isEmpty() && optional2.isEmpty()) {
            return p_330468_;
        } else {
            Dynamic<T> dynamic = p_330468_.emptyMap();
            if (optional.isPresent()) {
                dynamic = dynamic.set("custom_color", optional.get());
            }

            if (optional1.isPresent()) {
                dynamic = dynamic.set("custom_effects", optional1.get());
            }

            if (optional2.isPresent()) {
                dynamic = dynamic.set("potion", optional2.get());
            }

            return p_330468_.set("potion_contents", dynamic);
        }
    }
}
