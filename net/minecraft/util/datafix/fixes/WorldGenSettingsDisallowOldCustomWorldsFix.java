package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.Map;
import net.minecraft.nbt.NbtFormatException;

public class WorldGenSettingsDisallowOldCustomWorldsFix extends DataFix {
    public WorldGenSettingsDisallowOldCustomWorldsFix(Schema p_185157_) {
        super(p_185157_, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.WORLD_GEN_SETTINGS);
        OpticFinder<?> opticfinder = type.findField("dimensions");
        return this.fixTypeEverywhereTyped(
            "WorldGenSettingsDisallowOldCustomWorldsFix_" + this.getOutputSchema().getVersionKey(),
            type,
            p_185160_ -> p_185160_.updateTyped(opticfinder, p_337671_ -> {
                    p_337671_.write().map(p_337670_ -> p_337670_.getMapValues().map(p_185169_ -> {
                            p_185169_.forEach((p_341967_, p_341968_) -> {
                                if (p_341968_.get("type").asString().result().isEmpty()) {
                                    throw new NbtFormatException("Unable load old custom worlds.");
                                }
                            });
                            return p_185169_;
                        }));
                    return p_337671_;
                })
        );
    }
}
