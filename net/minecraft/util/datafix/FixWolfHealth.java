package net.minecraft.util.datafix;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.datafix.fixes.NamedEntityFix;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.util.datafix.schemas.NamespacedSchema;
import org.apache.commons.lang3.mutable.MutableBoolean;

public class FixWolfHealth extends NamedEntityFix {
    private static final String WOLF_ID = "minecraft:wolf";
    private static final String WOLF_HEALTH = "minecraft:generic.max_health";

    public FixWolfHealth(Schema p_326043_) {
        super(p_326043_, false, "FixWolfHealth", References.ENTITY, "minecraft:wolf");
    }

    @Override
    protected Typed<?> fix(Typed<?> p_326404_) {
        return p_326404_.update(
            DSL.remainderFinder(),
            p_326176_ -> {
                MutableBoolean mutableboolean = new MutableBoolean(false);
                p_326176_ = p_326176_.update(
                    "Attributes",
                    p_326144_ -> p_326144_.createList(
                            p_326144_.asStream()
                                .map(
                                    p_326758_ -> "minecraft:generic.max_health".equals(NamespacedSchema.ensureNamespaced(p_326758_.get("Name").asString("")))
                                            ? p_326758_.update("Base", p_325971_ -> {
                                                if (p_325971_.asDouble(0.0) == 20.0) {
                                                    mutableboolean.setTrue();
                                                    return p_325971_.createDouble(40.0);
                                                } else {
                                                    return p_325971_;
                                                }
                                            })
                                            : p_326758_
                                )
                        )
                );
                if (mutableboolean.isTrue()) {
                    p_326176_ = p_326176_.update("Health", p_326145_ -> p_326145_.createFloat(p_326145_.asFloat(0.0F) * 2.0F));
                }

                return p_326176_;
            }
        );
    }
}
