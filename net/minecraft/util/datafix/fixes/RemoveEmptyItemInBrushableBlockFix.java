package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class RemoveEmptyItemInBrushableBlockFix extends NamedEntityWriteReadFix {
    public RemoveEmptyItemInBrushableBlockFix(Schema p_342003_) {
        super(p_342003_, false, "RemoveEmptyItemInSuspiciousBlockFix", References.BLOCK_ENTITY, "minecraft:brushable_block");
    }

    @Override
    protected <T> Dynamic<T> fix(Dynamic<T> p_342013_) {
        Optional<Dynamic<T>> optional = p_342013_.get("item").result();
        return optional.isPresent() && isEmptyStack(optional.get()) ? p_342013_.remove("item") : p_342013_;
    }

    private static boolean isEmptyStack(Dynamic<?> p_342006_) {
        String s = NamespacedSchema.ensureNamespaced(p_342006_.get("id").asString("minecraft:air"));
        int i = p_342006_.get("count").asInt(0);
        return s.equals("minecraft:air") || i == 0;
    }
}
