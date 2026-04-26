package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Map;
import java.util.Optional;

public class PrimedTntBlockStateFixer extends NamedEntityWriteReadFix {
    public PrimedTntBlockStateFixer(Schema p_307306_) {
        super(p_307306_, true, "PrimedTnt BlockState fixer", References.ENTITY, "minecraft:tnt");
    }

    private static <T> Dynamic<T> renameFuse(Dynamic<T> p_307631_) {
        Optional<Dynamic<T>> optional = p_307631_.get("Fuse").get().result();
        return optional.isPresent() ? p_307631_.set("fuse", optional.get()) : p_307631_;
    }

    private static <T> Dynamic<T> insertBlockState(Dynamic<T> p_307568_) {
        return p_307568_.set("block_state", p_307568_.createMap(Map.of(p_307568_.createString("Name"), p_307568_.createString("minecraft:tnt"))));
    }

    @Override
    protected <T> Dynamic<T> fix(Dynamic<T> p_307650_) {
        return renameFuse(insertBlockState(p_307650_));
    }
}
