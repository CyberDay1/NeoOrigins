package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.OptionalDynamic;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.util.datafix.ComponentDataFixUtils;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class ItemStackCustomNameToOverrideComponentFix extends DataFix {
    private static final Set<String> MAP_NAMES = Set.of(
        "filled_map.buried_treasure",
        "filled_map.explorer_jungle",
        "filled_map.explorer_swamp",
        "filled_map.mansion",
        "filled_map.monument",
        "filled_map.trial_chambers",
        "filled_map.village_desert",
        "filled_map.village_plains",
        "filled_map.village_savanna",
        "filled_map.village_snowy",
        "filled_map.village_taiga"
    );

    public ItemStackCustomNameToOverrideComponentFix(Schema p_338575_) {
        super(p_338575_, false);
    }

    @Override
    public final TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<Pair<String, String>> opticfinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        OpticFinder<?> opticfinder1 = type.findField("components");
        return this.fixTypeEverywhereTyped(
            "ItemStack custom_name to item_name component fix",
            type,
            p_338745_ -> {
                Optional<Pair<String, String>> optional = p_338745_.getOptional(opticfinder);
                Optional<String> optional1 = optional.map(Pair::getSecond);
                if (optional1.filter(p_338283_ -> p_338283_.equals("minecraft:white_banner")).isPresent()) {
                    return p_338745_.updateTyped(
                        opticfinder1, p_338300_ -> p_338300_.update(DSL.remainderFinder(), ItemStackCustomNameToOverrideComponentFix::fixBanner)
                    );
                } else {
                    return optional1.filter(p_338490_ -> p_338490_.equals("minecraft:filled_map")).isPresent()
                        ? p_338745_.updateTyped(
                            opticfinder1, p_338256_ -> p_338256_.update(DSL.remainderFinder(), ItemStackCustomNameToOverrideComponentFix::fixMap)
                        )
                        : p_338745_;
                }
            }
        );
    }

    private static <T> Dynamic<T> fixMap(Dynamic<T> p_338642_) {
        return fixCustomName(p_338642_, MAP_NAMES::contains);
    }

    private static <T> Dynamic<T> fixBanner(Dynamic<T> p_338538_) {
        return fixCustomName(p_338538_, p_338711_ -> p_338711_.equals("block.minecraft.ominous_banner"));
    }

    private static <T> Dynamic<T> fixCustomName(Dynamic<T> p_338290_, Predicate<String> p_338596_) {
        OptionalDynamic<T> optionaldynamic = p_338290_.get("minecraft:custom_name");
        Optional<String> optional = optionaldynamic.asString().result().flatMap(ComponentDataFixUtils::extractTranslationString).filter(p_338596_);
        return optional.isPresent() ? p_338290_.renameField("minecraft:custom_name", "minecraft:item_name") : p_338290_;
    }
}
