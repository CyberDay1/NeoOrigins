package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class BlockPosFormatAndRenamesFix extends DataFix {
    private static final List<String> PATROLLING_MOBS = List.of(
        "minecraft:witch", "minecraft:ravager", "minecraft:pillager", "minecraft:illusioner", "minecraft:evoker", "minecraft:vindicator"
    );

    public BlockPosFormatAndRenamesFix(Schema p_326146_) {
        super(p_326146_, false);
    }

    private Typed<?> fixFields(Typed<?> p_326273_, Map<String, String> p_326439_) {
        return p_326273_.update(DSL.remainderFinder(), p_337600_ -> {
            for (Entry<String, String> entry : p_326439_.entrySet()) {
                p_337600_ = p_337600_.renameAndFixField(entry.getKey(), entry.getValue(), ExtraDataFixUtils::fixBlockPos);
            }

            return p_337600_;
        });
    }

    private <T> Dynamic<T> fixMapSavedData(Dynamic<T> p_326466_) {
        return p_326466_.update("frames", p_326158_ -> p_326158_.createList(p_326158_.asStream().map(p_337598_ -> {
                p_337598_ = p_337598_.renameAndFixField("Pos", "pos", ExtraDataFixUtils::fixBlockPos);
                p_337598_ = p_337598_.renameField("Rotation", "rotation");
                return p_337598_.renameField("EntityId", "entity_id");
            }))).update("banners", p_326387_ -> p_326387_.createList(p_326387_.asStream().map(p_337601_ -> {
                p_337601_ = p_337601_.renameField("Pos", "pos");
                p_337601_ = p_337601_.renameField("Color", "color");
                return p_337601_.renameField("Name", "name");
            })));
    }

    @Override
    public TypeRewriteRule makeRule() {
        List<TypeRewriteRule> list = new ArrayList<>();
        this.addEntityRules(list);
        this.addBlockEntityRules(list);
        list.add(
            this.fixTypeEverywhereTyped(
                "BlockPos format for map frames",
                this.getInputSchema().getType(References.SAVED_DATA_MAP_DATA),
                p_326202_ -> p_326202_.update(DSL.remainderFinder(), p_326364_ -> p_326364_.update("data", this::fixMapSavedData))
            )
        );
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        list.add(
            this.fixTypeEverywhereTyped(
                "BlockPos format for compass target",
                type,
                ItemStackTagFix.createFixer(type, "minecraft:compass"::equals, p_326048_ -> p_326048_.update("LodestonePos", ExtraDataFixUtils::fixBlockPos))
            )
        );
        return TypeRewriteRule.seq(list);
    }

    private void addEntityRules(List<TypeRewriteRule> p_326170_) {
        p_326170_.add(this.createEntityFixer(References.ENTITY, "minecraft:bee", Map.of("HivePos", "hive_pos", "FlowerPos", "flower_pos")));
        p_326170_.add(this.createEntityFixer(References.ENTITY, "minecraft:end_crystal", Map.of("BeamTarget", "beam_target")));
        p_326170_.add(this.createEntityFixer(References.ENTITY, "minecraft:wandering_trader", Map.of("WanderTarget", "wander_target")));

        for (String s : PATROLLING_MOBS) {
            p_326170_.add(this.createEntityFixer(References.ENTITY, s, Map.of("PatrolTarget", "patrol_target")));
        }

        p_326170_.add(
            this.fixTypeEverywhereTyped(
                "BlockPos format in Leash for mobs",
                this.getInputSchema().getType(References.ENTITY),
                p_326408_ -> p_326408_.update(DSL.remainderFinder(), p_337602_ -> p_337602_.renameAndFixField("Leash", "leash", ExtraDataFixUtils::fixBlockPos))
            )
        );
    }

    private void addBlockEntityRules(List<TypeRewriteRule> p_326031_) {
        p_326031_.add(this.createEntityFixer(References.BLOCK_ENTITY, "minecraft:beehive", Map.of("FlowerPos", "flower_pos")));
        p_326031_.add(this.createEntityFixer(References.BLOCK_ENTITY, "minecraft:end_gateway", Map.of("ExitPortal", "exit_portal")));
    }

    private TypeRewriteRule createEntityFixer(TypeReference p_326126_, String p_326263_, Map<String, String> p_326382_) {
        String s = "BlockPos format in " + p_326382_.keySet() + " for " + p_326263_ + " (" + p_326126_.typeName() + ")";
        OpticFinder<?> opticfinder = DSL.namedChoice(p_326263_, this.getInputSchema().getChoiceType(p_326126_, p_326263_));
        return this.fixTypeEverywhereTyped(
            s, this.getInputSchema().getType(p_326126_), p_325999_ -> p_325999_.updateTyped(opticfinder, p_326318_ -> this.fixFields(p_326318_, p_326382_))
        );
    }
}
