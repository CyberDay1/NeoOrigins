package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class MapBannerBlockPosFormatFix extends DataFix {
    public MapBannerBlockPosFormatFix(Schema p_326011_) {
        super(p_326011_, false);
    }

    private static <T> Dynamic<T> fixMapSavedData(Dynamic<T> p_326259_) {
        return p_326259_.update(
            "banners", p_326346_ -> p_326346_.createList(p_326346_.asStream().map(p_326061_ -> p_326061_.update("Pos", ExtraDataFixUtils::fixBlockPos)))
        );
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "MapBannerBlockPosFormatFix",
            this.getInputSchema().getType(References.SAVED_DATA_MAP_DATA),
            p_325992_ -> p_325992_.update(DSL.remainderFinder(), p_326422_ -> p_326422_.update("data", MapBannerBlockPosFormatFix::fixMapSavedData))
        );
    }
}
