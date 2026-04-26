package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class EntityPaintingFieldsRenameFix extends NamedEntityFix {
    public EntityPaintingFieldsRenameFix(Schema p_216606_) {
        super(p_216606_, false, "EntityPaintingFieldsRenameFix", References.ENTITY, "minecraft:painting");
    }

    public Dynamic<?> fixTag(Dynamic<?> p_216610_) {
        return p_216610_.renameField("Motive", "variant").renameField("Facing", "facing");
    }

    @Override
    protected Typed<?> fix(Typed<?> p_216608_) {
        return p_216608_.update(DSL.remainderFinder(), this::fixTag);
    }
}
