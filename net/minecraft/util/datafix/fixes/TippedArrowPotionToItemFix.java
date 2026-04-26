package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class TippedArrowPotionToItemFix extends NamedEntityWriteReadFix {
    public TippedArrowPotionToItemFix(Schema p_330412_) {
        super(p_330412_, false, "TippedArrowPotionToItemFix", References.ENTITY, "minecraft:arrow");
    }

    @Override
    protected <T> Dynamic<T> fix(Dynamic<T> p_330283_) {
        Optional<Dynamic<T>> optional = p_330283_.get("Potion").result();
        Optional<Dynamic<T>> optional1 = p_330283_.get("custom_potion_effects").result();
        Optional<Dynamic<T>> optional2 = p_330283_.get("Color").result();
        return optional.isEmpty() && optional1.isEmpty() && optional2.isEmpty()
            ? p_330283_
            : p_330283_.remove("Potion").remove("custom_potion_effects").remove("Color").update("item", p_331889_ -> {
                Dynamic<?> dynamic = p_331889_.get("tag").orElseEmptyMap();
                if (optional.isPresent()) {
                    dynamic = dynamic.set("Potion", optional.get());
                }

                if (optional1.isPresent()) {
                    dynamic = dynamic.set("custom_potion_effects", optional1.get());
                }

                if (optional2.isPresent()) {
                    dynamic = dynamic.set("CustomPotionColor", optional2.get());
                }

                return p_331889_.set("tag", dynamic);
            });
    }
}
