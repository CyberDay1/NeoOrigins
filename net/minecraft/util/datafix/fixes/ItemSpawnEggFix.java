package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class ItemSpawnEggFix extends DataFix {
    private static final String[] ID_TO_ENTITY = DataFixUtils.make(new String[256], p_341874_ -> {
        p_341874_[1] = "Item";
        p_341874_[2] = "XPOrb";
        p_341874_[7] = "ThrownEgg";
        p_341874_[8] = "LeashKnot";
        p_341874_[9] = "Painting";
        p_341874_[10] = "Arrow";
        p_341874_[11] = "Snowball";
        p_341874_[12] = "Fireball";
        p_341874_[13] = "SmallFireball";
        p_341874_[14] = "ThrownEnderpearl";
        p_341874_[15] = "EyeOfEnderSignal";
        p_341874_[16] = "ThrownPotion";
        p_341874_[17] = "ThrownExpBottle";
        p_341874_[18] = "ItemFrame";
        p_341874_[19] = "WitherSkull";
        p_341874_[20] = "PrimedTnt";
        p_341874_[21] = "FallingSand";
        p_341874_[22] = "FireworksRocketEntity";
        p_341874_[23] = "TippedArrow";
        p_341874_[24] = "SpectralArrow";
        p_341874_[25] = "ShulkerBullet";
        p_341874_[26] = "DragonFireball";
        p_341874_[30] = "ArmorStand";
        p_341874_[41] = "Boat";
        p_341874_[42] = "MinecartRideable";
        p_341874_[43] = "MinecartChest";
        p_341874_[44] = "MinecartFurnace";
        p_341874_[45] = "MinecartTNT";
        p_341874_[46] = "MinecartHopper";
        p_341874_[47] = "MinecartSpawner";
        p_341874_[40] = "MinecartCommandBlock";
        p_341874_[50] = "Creeper";
        p_341874_[51] = "Skeleton";
        p_341874_[52] = "Spider";
        p_341874_[53] = "Giant";
        p_341874_[54] = "Zombie";
        p_341874_[55] = "Slime";
        p_341874_[56] = "Ghast";
        p_341874_[57] = "PigZombie";
        p_341874_[58] = "Enderman";
        p_341874_[59] = "CaveSpider";
        p_341874_[60] = "Silverfish";
        p_341874_[61] = "Blaze";
        p_341874_[62] = "LavaSlime";
        p_341874_[63] = "EnderDragon";
        p_341874_[64] = "WitherBoss";
        p_341874_[65] = "Bat";
        p_341874_[66] = "Witch";
        p_341874_[67] = "Endermite";
        p_341874_[68] = "Guardian";
        p_341874_[69] = "Shulker";
        p_341874_[90] = "Pig";
        p_341874_[91] = "Sheep";
        p_341874_[92] = "Cow";
        p_341874_[93] = "Chicken";
        p_341874_[94] = "Squid";
        p_341874_[95] = "Wolf";
        p_341874_[96] = "MushroomCow";
        p_341874_[97] = "SnowMan";
        p_341874_[98] = "Ozelot";
        p_341874_[99] = "VillagerGolem";
        p_341874_[100] = "EntityHorse";
        p_341874_[101] = "Rabbit";
        p_341874_[120] = "Villager";
        p_341874_[200] = "EnderCrystal";
    });

    public ItemSpawnEggFix(Schema p_16034_, boolean p_16035_) {
        super(p_16034_, p_16035_);
    }

    @Override
    public TypeRewriteRule makeRule() {
        Schema schema = this.getInputSchema();
        Type<?> type = schema.getType(References.ITEM_STACK);
        OpticFinder<Pair<String, String>> opticfinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        OpticFinder<String> opticfinder1 = DSL.fieldFinder("id", DSL.string());
        OpticFinder<?> opticfinder2 = type.findField("tag");
        OpticFinder<?> opticfinder3 = opticfinder2.type().findField("EntityTag");
        OpticFinder<?> opticfinder4 = DSL.typeFinder(schema.getTypeRaw(References.ENTITY));
        Type<?> type1 = this.getOutputSchema().getTypeRaw(References.ENTITY);
        return this.fixTypeEverywhereTyped("ItemSpawnEggFix", type, p_311557_ -> {
            Optional<Pair<String, String>> optional = p_311557_.getOptional(opticfinder);
            if (optional.isPresent() && Objects.equals(optional.get().getSecond(), "minecraft:spawn_egg")) {
                Dynamic<?> dynamic = p_311557_.get(DSL.remainderFinder());
                short short1 = dynamic.get("Damage").asShort((short)0);
                Optional<? extends Typed<?>> optional1 = p_311557_.getOptionalTyped(opticfinder2);
                Optional<? extends Typed<?>> optional2 = optional1.flatMap(p_145417_ -> p_145417_.getOptionalTyped(opticfinder3));
                Optional<? extends Typed<?>> optional3 = optional2.flatMap(p_145414_ -> p_145414_.getOptionalTyped(opticfinder4));
                Optional<String> optional4 = optional3.flatMap(p_145406_ -> p_145406_.getOptional(opticfinder1));
                Typed<?> typed = p_311557_;
                String s = ID_TO_ENTITY[short1 & 255];
                if (s != null && (optional4.isEmpty() || !Objects.equals(optional4.get(), s))) {
                    Typed<?> typed1 = p_311557_.getOrCreateTyped(opticfinder2);
                    Typed<?> typed2 = typed1.getOrCreateTyped(opticfinder3);
                    Typed<?> typed3 = typed2.getOrCreateTyped(opticfinder4);
                    Dynamic<?> dynamic_f = dynamic;
                    Typed<?> typed4 = Util.writeAndReadTypedOrThrow(typed3, type1, p_311560_ -> p_311560_.set("id", dynamic_f.createString(s)));
                    typed = p_311557_.set(opticfinder2, typed1.set(opticfinder3, typed2.set(opticfinder4, typed4)));
                }

                if (short1 != 0) {
                    dynamic = dynamic.set("Damage", dynamic.createShort((short)0));
                    typed = typed.set(DSL.remainderFinder(), dynamic);
                }

                return typed;
            } else {
                return p_311557_;
            }
        });
    }
}
