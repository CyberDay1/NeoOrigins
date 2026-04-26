package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class MobEffectIdFix extends DataFix {
    private static final Int2ObjectMap<String> ID_MAP = Util.make(new Int2ObjectOpenHashMap<>(), p_298903_ -> {
        p_298903_.put(1, "minecraft:speed");
        p_298903_.put(2, "minecraft:slowness");
        p_298903_.put(3, "minecraft:haste");
        p_298903_.put(4, "minecraft:mining_fatigue");
        p_298903_.put(5, "minecraft:strength");
        p_298903_.put(6, "minecraft:instant_health");
        p_298903_.put(7, "minecraft:instant_damage");
        p_298903_.put(8, "minecraft:jump_boost");
        p_298903_.put(9, "minecraft:nausea");
        p_298903_.put(10, "minecraft:regeneration");
        p_298903_.put(11, "minecraft:resistance");
        p_298903_.put(12, "minecraft:fire_resistance");
        p_298903_.put(13, "minecraft:water_breathing");
        p_298903_.put(14, "minecraft:invisibility");
        p_298903_.put(15, "minecraft:blindness");
        p_298903_.put(16, "minecraft:night_vision");
        p_298903_.put(17, "minecraft:hunger");
        p_298903_.put(18, "minecraft:weakness");
        p_298903_.put(19, "minecraft:poison");
        p_298903_.put(20, "minecraft:wither");
        p_298903_.put(21, "minecraft:health_boost");
        p_298903_.put(22, "minecraft:absorption");
        p_298903_.put(23, "minecraft:saturation");
        p_298903_.put(24, "minecraft:glowing");
        p_298903_.put(25, "minecraft:levitation");
        p_298903_.put(26, "minecraft:luck");
        p_298903_.put(27, "minecraft:unluck");
        p_298903_.put(28, "minecraft:slow_falling");
        p_298903_.put(29, "minecraft:conduit_power");
        p_298903_.put(30, "minecraft:dolphins_grace");
        p_298903_.put(31, "minecraft:bad_omen");
        p_298903_.put(32, "minecraft:hero_of_the_village");
        p_298903_.put(33, "minecraft:darkness");
    });
    private static final Set<String> MOB_EFFECT_INSTANCE_CARRIER_ITEMS = Set.of(
        "minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:tipped_arrow"
    );

    public MobEffectIdFix(Schema p_298197_) {
        super(p_298197_, false);
    }

    private static <T> Optional<Dynamic<T>> getAndConvertMobEffectId(Dynamic<T> p_299296_, String p_298445_) {
        return p_299296_.get(p_298445_).asNumber().result().map(p_298383_ -> ID_MAP.get(p_298383_.intValue())).map(p_299296_::createString);
    }

    private static <T> Dynamic<T> updateMobEffectIdField(Dynamic<T> p_298948_, String p_299234_, Dynamic<T> p_298832_, String p_298487_) {
        Optional<Dynamic<T>> optional = getAndConvertMobEffectId(p_298948_, p_299234_);
        return p_298832_.replaceField(p_299234_, p_298487_, optional);
    }

    private static <T> Dynamic<T> updateMobEffectIdField(Dynamic<T> p_299001_, String p_298420_, String p_299179_) {
        return updateMobEffectIdField(p_299001_, p_298420_, p_299001_, p_299179_);
    }

    private static <T> Dynamic<T> updateMobEffectIdFieldConsideringForge(Dynamic<T> p_298948_, String p_299234_, Dynamic<T> p_298832_, String p_298487_, String forgeFieldId) {
        final var forgeField = p_298948_.get(forgeFieldId).result();
        if (forgeField.isPresent()) {
                return setFieldIfPresent((p_298948_ == p_298832_ ? p_298948_.remove(forgeFieldId) : p_298832_), p_298487_, forgeField);
            }
        return updateMobEffectIdField(p_298948_, p_299234_, p_298832_, p_298487_);
    }

    private static <T> Dynamic<T> setFieldIfPresent(Dynamic<T> dynamic, String s, Optional<Dynamic<T>> optional) {
        return optional.isEmpty() ? dynamic : dynamic.set(s, optional.get());
    }

    private static <T> Dynamic<T> updateMobEffectInstance(Dynamic<T> p_298320_) {
        p_298320_ = updateMobEffectIdFieldConsideringForge(p_298320_, "Id", p_298320_, "id", "forge:id");
        p_298320_ = p_298320_.renameField("Ambient", "ambient");
        p_298320_ = p_298320_.renameField("Amplifier", "amplifier");
        p_298320_ = p_298320_.renameField("Duration", "duration");
        p_298320_ = p_298320_.renameField("ShowParticles", "show_particles");
        p_298320_ = p_298320_.renameField("ShowIcon", "show_icon");
        Optional<Dynamic<T>> optional = p_298320_.get("HiddenEffect").result().map(MobEffectIdFix::updateMobEffectInstance);
        return p_298320_.replaceField("HiddenEffect", "hidden_effect", optional);
    }

    private static <T> Dynamic<T> updateMobEffectInstanceList(Dynamic<T> p_299048_, String p_298254_, String p_298643_) {
        Optional<Dynamic<T>> optional = p_299048_.get(p_298254_)
            .asStreamOpt()
            .result()
            .map(p_298291_ -> p_299048_.createList(p_298291_.map(MobEffectIdFix::updateMobEffectInstance)));
        return p_299048_.replaceField(p_298254_, p_298643_, optional);
    }

    private static <T> Dynamic<T> updateSuspiciousStewEntry(Dynamic<T> p_298902_, Dynamic<T> p_299113_) {
        p_299113_ = updateMobEffectIdFieldConsideringForge(p_298902_, "EffectId", p_299113_, "id", "forge:effect_id");
        Optional<Dynamic<T>> optional = p_298902_.get("EffectDuration").result();
        return p_299113_.replaceField("EffectDuration", "duration", optional);
    }

    private static <T> Dynamic<T> updateSuspiciousStewEntry(Dynamic<T> p_298873_) {
        return updateSuspiciousStewEntry(p_298873_, p_298873_);
    }

    private Typed<?> updateNamedChoice(Typed<?> p_298304_, TypeReference p_298928_, String p_298718_, Function<Dynamic<?>, Dynamic<?>> p_298931_) {
        Type<?> type = this.getInputSchema().getChoiceType(p_298928_, p_298718_);
        Type<?> type1 = this.getOutputSchema().getChoiceType(p_298928_, p_298718_);
        return p_298304_.updateTyped(DSL.namedChoice(p_298718_, type), type1, p_298322_ -> p_298322_.update(DSL.remainderFinder(), p_298931_));
    }

    private TypeRewriteRule blockEntityFixer() {
        Type<?> type = this.getInputSchema().getType(References.BLOCK_ENTITY);
        return this.fixTypeEverywhereTyped(
            "BlockEntityMobEffectIdFix", type, p_299097_ -> this.updateNamedChoice(p_299097_, References.BLOCK_ENTITY, "minecraft:beacon", p_298966_ -> {
                    p_298966_ = updateMobEffectIdField(p_298966_, "Primary", "primary_effect");
                    return updateMobEffectIdField(p_298966_, "Secondary", "secondary_effect");
                })
        );
    }

    private static <T> Dynamic<T> fixMooshroomTag(Dynamic<T> p_298470_) {
        Dynamic<T> dynamic = p_298470_.emptyMap();
        Dynamic<T> dynamic1 = updateSuspiciousStewEntry(p_298470_, dynamic);
        if (!dynamic1.equals(dynamic)) {
            p_298470_ = p_298470_.set("stew_effects", p_298470_.createList(Stream.of(dynamic1)));
        }

        return p_298470_.remove("EffectId").remove("EffectDuration");
    }

    private static <T> Dynamic<T> fixArrowTag(Dynamic<T> p_299026_) {
        return updateMobEffectInstanceList(p_299026_, "CustomPotionEffects", "custom_potion_effects");
    }

    private static <T> Dynamic<T> fixAreaEffectCloudTag(Dynamic<T> p_298539_) {
        return updateMobEffectInstanceList(p_298539_, "Effects", "effects");
    }

    private static Dynamic<?> updateLivingEntityTag(Dynamic<?> p_299145_) {
        return updateMobEffectInstanceList(p_299145_, "ActiveEffects", "active_effects");
    }

    private TypeRewriteRule entityFixer() {
        Type<?> type = this.getInputSchema().getType(References.ENTITY);
        return this.fixTypeEverywhereTyped("EntityMobEffectIdFix", type, p_298626_ -> {
            p_298626_ = this.updateNamedChoice(p_298626_, References.ENTITY, "minecraft:mooshroom", MobEffectIdFix::fixMooshroomTag);
            p_298626_ = this.updateNamedChoice(p_298626_, References.ENTITY, "minecraft:arrow", MobEffectIdFix::fixArrowTag);
            p_298626_ = this.updateNamedChoice(p_298626_, References.ENTITY, "minecraft:area_effect_cloud", MobEffectIdFix::fixAreaEffectCloudTag);
            return p_298626_.update(DSL.remainderFinder(), MobEffectIdFix::updateLivingEntityTag);
        });
    }

    private TypeRewriteRule playerFixer() {
        Type<?> type = this.getInputSchema().getType(References.PLAYER);
        return this.fixTypeEverywhereTyped(
            "PlayerMobEffectIdFix", type, p_300792_ -> p_300792_.update(DSL.remainderFinder(), MobEffectIdFix::updateLivingEntityTag)
        );
    }

    private static <T> Dynamic<T> fixSuspiciousStewTag(Dynamic<T> p_298546_) {
        Optional<Dynamic<T>> optional = p_298546_.get("Effects")
            .asStreamOpt()
            .result()
            .map(p_299036_ -> p_298546_.createList(p_299036_.map(MobEffectIdFix::updateSuspiciousStewEntry)));
        return p_298546_.replaceField("Effects", "effects", optional);
    }

    private TypeRewriteRule itemStackFixer() {
        OpticFinder<Pair<String, String>> opticfinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticfinder1 = type.findField("tag");
        return this.fixTypeEverywhereTyped(
            "ItemStackMobEffectIdFix",
            type,
            p_298821_ -> {
                Optional<Pair<String, String>> optional = p_298821_.getOptional(opticfinder);
                if (optional.isPresent()) {
                    String s = optional.get().getSecond();
                    if (s.equals("minecraft:suspicious_stew")) {
                        return p_298821_.updateTyped(opticfinder1, p_298520_ -> p_298520_.update(DSL.remainderFinder(), MobEffectIdFix::fixSuspiciousStewTag));
                    }

                    if (MOB_EFFECT_INSTANCE_CARRIER_ITEMS.contains(s)) {
                        return p_298821_.updateTyped(
                            opticfinder1,
                            p_298705_ -> p_298705_.update(
                                    DSL.remainderFinder(), p_298855_ -> updateMobEffectInstanceList(p_298855_, "CustomPotionEffects", "custom_potion_effects")
                                )
                        );
                    }
                }

                return p_298821_;
            }
        );
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return TypeRewriteRule.seq(this.blockEntityFixer(), this.entityFixer(), this.playerFixer(), this.itemStackFixer());
    }
}
