package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import net.minecraft.Util;

public class EntityMinecartIdentifiersFix extends EntityRenameFix {
    public EntityMinecartIdentifiersFix(Schema p_15479_) {
        super("EntityMinecartIdentifiersFix", p_15479_, true);
    }

    @Override
    protected Pair<String, Typed<?>> fix(String p_341981_, Typed<?> p_341975_) {
        if (!p_341981_.equals("Minecart")) {
            return Pair.of(p_341981_, p_341975_);
        } else {
            int i = p_341975_.getOrCreate(DSL.remainderFinder()).get("Type").asInt(0);

            String s = switch (i) {
                case 1 -> "MinecartChest";
                case 2 -> "MinecartFurnace";
                default -> "MinecartRideable";
            };
            Type<?> type = this.getOutputSchema().findChoiceType(References.ENTITY).types().get(s);
            return Pair.of(s, Util.writeAndReadTypedOrThrow(p_341975_, type, p_341966_ -> p_341966_.remove("Type")));
        }
    }
}
