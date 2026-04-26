package net.minecraft.util.datafix.fixes;

import com.google.common.base.Splitter;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.OptionalDynamic;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.ComponentDataFixUtils;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class ItemStackComponentizationFix extends DataFix {
    private static final int HIDE_ENCHANTMENTS = 1;
    private static final int HIDE_MODIFIERS = 2;
    private static final int HIDE_UNBREAKABLE = 4;
    private static final int HIDE_CAN_DESTROY = 8;
    private static final int HIDE_CAN_PLACE = 16;
    private static final int HIDE_ADDITIONAL = 32;
    private static final int HIDE_DYE = 64;
    private static final int HIDE_UPGRADES = 128;
    private static final Set<String> POTION_HOLDER_IDS = Set.of(
        "minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:tipped_arrow"
    );
    private static final Set<String> BUCKETED_MOB_IDS = Set.of(
        "minecraft:pufferfish_bucket",
        "minecraft:salmon_bucket",
        "minecraft:cod_bucket",
        "minecraft:tropical_fish_bucket",
        "minecraft:axolotl_bucket",
        "minecraft:tadpole_bucket"
    );
    private static final List<String> BUCKETED_MOB_TAGS = List.of(
        "NoAI", "Silent", "NoGravity", "Glowing", "Invulnerable", "Health", "Age", "Variant", "HuntingCooldown", "BucketVariantTag"
    );
    private static final Set<String> BOOLEAN_BLOCK_STATE_PROPERTIES = Set.of(
        "attached",
        "bottom",
        "conditional",
        "disarmed",
        "drag",
        "enabled",
        "extended",
        "eye",
        "falling",
        "hanging",
        "has_bottle_0",
        "has_bottle_1",
        "has_bottle_2",
        "has_record",
        "has_book",
        "inverted",
        "in_wall",
        "lit",
        "locked",
        "occupied",
        "open",
        "persistent",
        "powered",
        "short",
        "signal_fire",
        "snowy",
        "triggered",
        "unstable",
        "waterlogged",
        "berries",
        "bloom",
        "shrieking",
        "can_summon",
        "up",
        "down",
        "north",
        "east",
        "south",
        "west",
        "slot_0_occupied",
        "slot_1_occupied",
        "slot_2_occupied",
        "slot_3_occupied",
        "slot_4_occupied",
        "slot_5_occupied",
        "cracked",
        "crafting"
    );
    private static final Splitter PROPERTY_SPLITTER = Splitter.on(',');

    public ItemStackComponentizationFix(Schema p_331673_) {
        super(p_331673_, true);
    }

    private static void fixItemStack(ItemStackComponentizationFix.ItemStackData p_332167_, Dynamic<?> p_330498_) {
        int i = p_332167_.removeTag("HideFlags").asInt(0);
        p_332167_.moveTagToComponent("Damage", "minecraft:damage", p_330498_.createInt(0));
        p_332167_.moveTagToComponent("RepairCost", "minecraft:repair_cost", p_330498_.createInt(0));
        p_332167_.moveTagToComponent("CustomModelData", "minecraft:custom_model_data");
        p_332167_.removeTag("BlockStateTag")
            .result()
            .ifPresent(p_332594_ -> p_332167_.setComponent("minecraft:block_state", fixBlockStateTag((Dynamic<?>)p_332594_)));
        p_332167_.moveTagToComponent("EntityTag", "minecraft:entity_data");
        p_332167_.fixSubTag("BlockEntityTag", false, p_330655_ -> {
            String s = NamespacedSchema.ensureNamespaced(p_330655_.get("id").asString(""));
            p_330655_ = fixBlockEntityTag(p_332167_, p_330655_, s);
            Dynamic<?> dynamic2 = p_330655_.remove("id");
            return dynamic2.equals(p_330655_.emptyMap()) ? dynamic2 : p_330655_;
        });
        p_332167_.moveTagToComponent("BlockEntityTag", "minecraft:block_entity_data");
        if (p_332167_.removeTag("Unbreakable").asBoolean(false)) {
            Dynamic<?> dynamic = p_330498_.emptyMap();
            if ((i & 4) != 0) {
                dynamic = dynamic.set("show_in_tooltip", p_330498_.createBoolean(false));
            }

            p_332167_.setComponent("minecraft:unbreakable", dynamic);
        }

        fixEnchantments(p_332167_, p_330498_, "Enchantments", "minecraft:enchantments", (i & 1) != 0);
        if (p_332167_.is("minecraft:enchanted_book")) {
            fixEnchantments(p_332167_, p_330498_, "StoredEnchantments", "minecraft:stored_enchantments", (i & 32) != 0);
        }

        p_332167_.fixSubTag("display", false, p_331784_ -> fixDisplay(p_332167_, p_331784_, i));
        fixAdventureModeChecks(p_332167_, p_330498_, i);
        fixAttributeModifiers(p_332167_, p_330498_, i);
        Optional<? extends Dynamic<?>> optional = p_332167_.removeTag("Trim").result();
        if (optional.isPresent()) {
            Dynamic<?> dynamic1 = (Dynamic<?>)optional.get();
            if ((i & 128) != 0) {
                dynamic1 = dynamic1.set("show_in_tooltip", dynamic1.createBoolean(false));
            }

            p_332167_.setComponent("minecraft:trim", dynamic1);
        }

        if ((i & 32) != 0) {
            p_332167_.setComponent("minecraft:hide_additional_tooltip", p_330498_.emptyMap());
        }

        if (p_332167_.is("minecraft:crossbow")) {
            p_332167_.removeTag("Charged");
            p_332167_.moveTagToComponent("ChargedProjectiles", "minecraft:charged_projectiles", p_330498_.createList(Stream.empty()));
        }

        if (p_332167_.is("minecraft:bundle")) {
            p_332167_.moveTagToComponent("Items", "minecraft:bundle_contents", p_330498_.createList(Stream.empty()));
        }

        if (p_332167_.is("minecraft:filled_map")) {
            p_332167_.moveTagToComponent("map", "minecraft:map_id");
            Map<? extends Dynamic<?>, ? extends Dynamic<?>> map = p_332167_.removeTag("Decorations")
                .asStream()
                .map(ItemStackComponentizationFix::fixMapDecoration)
                .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond, (p_332591_, p_339605_) -> p_332591_));
            if (!map.isEmpty()) {
                p_332167_.setComponent("minecraft:map_decorations", p_330498_.createMap(map));
            }
        }

        if (p_332167_.is(POTION_HOLDER_IDS)) {
            fixPotionContents(p_332167_, p_330498_);
        }

        if (p_332167_.is("minecraft:writable_book")) {
            fixWritableBook(p_332167_, p_330498_);
        }

        if (p_332167_.is("minecraft:written_book")) {
            fixWrittenBook(p_332167_, p_330498_);
        }

        if (p_332167_.is("minecraft:suspicious_stew")) {
            p_332167_.moveTagToComponent("effects", "minecraft:suspicious_stew_effects");
        }

        if (p_332167_.is("minecraft:debug_stick")) {
            p_332167_.moveTagToComponent("DebugProperty", "minecraft:debug_stick_state");
        }

        if (p_332167_.is(BUCKETED_MOB_IDS)) {
            fixBucketedMobData(p_332167_, p_330498_);
        }

        if (p_332167_.is("minecraft:goat_horn")) {
            p_332167_.moveTagToComponent("instrument", "minecraft:instrument");
        }

        if (p_332167_.is("minecraft:knowledge_book")) {
            p_332167_.moveTagToComponent("Recipes", "minecraft:recipes");
        }

        if (p_332167_.is("minecraft:compass")) {
            fixLodestoneTracker(p_332167_, p_330498_);
        }

        if (p_332167_.is("minecraft:firework_rocket")) {
            fixFireworkRocket(p_332167_);
        }

        if (p_332167_.is("minecraft:firework_star")) {
            fixFireworkStar(p_332167_);
        }

        if (p_332167_.is("minecraft:player_head")) {
            p_332167_.removeTag("SkullOwner").result().ifPresent(p_330565_ -> p_332167_.setComponent("minecraft:profile", fixProfile((Dynamic<?>)p_330565_)));
        }
    }

    private static Dynamic<?> fixBlockStateTag(Dynamic<?> p_332829_) {
        return DataFixUtils.orElse(p_332829_.asMapOpt().result().map(p_339491_ -> p_339491_.collect(Collectors.toMap(Pair::getFirst, p_339490_ -> {
                String s = ((Dynamic)p_339490_.getFirst()).asString("");
                Dynamic<?> dynamic = (Dynamic<?>)p_339490_.getSecond();
                if (BOOLEAN_BLOCK_STATE_PROPERTIES.contains(s)) {
                    Optional<Boolean> optional = dynamic.asBoolean().result();
                    if (optional.isPresent()) {
                        return dynamic.createString(String.valueOf(optional.get()));
                    }
                }

                Optional<Number> optional1 = dynamic.asNumber().result();
                return optional1.isPresent() ? dynamic.createString(optional1.get().toString()) : dynamic;
            }))).map(p_332829_::createMap), p_332829_);
    }

    private static Dynamic<?> fixDisplay(ItemStackComponentizationFix.ItemStackData p_331302_, Dynamic<?> p_331703_, int p_331793_) {
        p_331302_.setComponent("minecraft:custom_name", p_331703_.get("Name"));
        p_331302_.setComponent("minecraft:lore", p_331703_.get("Lore"));
        Optional<Integer> optional = p_331703_.get("color").asNumber().result().map(Number::intValue);
        boolean flag = (p_331793_ & 64) != 0;
        if (optional.isPresent() || flag) {
            Dynamic<?> dynamic = p_331703_.emptyMap().set("rgb", p_331703_.createInt(optional.orElse(10511680)));
            if (flag) {
                dynamic = dynamic.set("show_in_tooltip", p_331703_.createBoolean(false));
            }

            p_331302_.setComponent("minecraft:dyed_color", dynamic);
        }

        Optional<String> optional1 = p_331703_.get("LocName").asString().result();
        if (optional1.isPresent()) {
            p_331302_.setComponent("minecraft:item_name", ComponentDataFixUtils.createTranslatableComponent(p_331703_.getOps(), optional1.get()));
        }

        if (p_331302_.is("minecraft:filled_map")) {
            p_331302_.setComponent("minecraft:map_color", p_331703_.get("MapColor"));
            p_331703_ = p_331703_.remove("MapColor");
        }

        return p_331703_.remove("Name").remove("Lore").remove("color").remove("LocName");
    }

    private static <T> Dynamic<T> fixBlockEntityTag(ItemStackComponentizationFix.ItemStackData p_330721_, Dynamic<T> p_331427_, String p_331421_) {
        p_330721_.setComponent("minecraft:lock", p_331427_.get("Lock"));
        p_331427_ = p_331427_.remove("Lock");
        Optional<Dynamic<T>> optional = p_331427_.get("LootTable").result();
        if (optional.isPresent()) {
            Dynamic<T> dynamic = p_331427_.emptyMap().set("loot_table", optional.get());
            long i = p_331427_.get("LootTableSeed").asLong(0L);
            if (i != 0L) {
                dynamic = dynamic.set("seed", p_331427_.createLong(i));
            }

            p_330721_.setComponent("minecraft:container_loot", dynamic);
            p_331427_ = p_331427_.remove("LootTable").remove("LootTableSeed");
        }
        return switch (p_331421_) {
            case "minecraft:skull" -> {
                p_330721_.setComponent("minecraft:note_block_sound", p_331427_.get("note_block_sound"));
                yield p_331427_.remove("note_block_sound");
            }
            case "minecraft:decorated_pot" -> {
                p_330721_.setComponent("minecraft:pot_decorations", p_331427_.get("sherds"));
                Optional<Dynamic<T>> optional2 = p_331427_.get("item").result();
                if (optional2.isPresent()) {
                    p_330721_.setComponent(
                        "minecraft:container",
                        p_331427_.createList(Stream.of(p_331427_.emptyMap().set("slot", p_331427_.createInt(0)).set("item", optional2.get())))
                    );
                }

                yield p_331427_.remove("sherds").remove("item");
            }
            case "minecraft:banner" -> {
                p_330721_.setComponent("minecraft:banner_patterns", p_331427_.get("patterns"));
                Optional<Number> optional1 = p_331427_.get("Base").asNumber().result();
                if (optional1.isPresent()) {
                    p_330721_.setComponent("minecraft:base_color", p_331427_.createString(BannerPatternFormatFix.fixColor(optional1.get().intValue())));
                }

                yield p_331427_.remove("patterns").remove("Base");
            }
            case "minecraft:shulker_box", "minecraft:chest", "minecraft:trapped_chest", "minecraft:furnace", "minecraft:ender_chest", "minecraft:dispenser", "minecraft:dropper", "minecraft:brewing_stand", "minecraft:hopper", "minecraft:barrel", "minecraft:smoker", "minecraft:blast_furnace", "minecraft:campfire", "minecraft:chiseled_bookshelf", "minecraft:crafter" -> {
                List<Dynamic<T>> list = p_331427_.get("Items")
                    .asList(
                        p_332590_ -> p_332590_.emptyMap()
                                .set("slot", p_332590_.createInt(p_332590_.get("Slot").asByte((byte)0) & 255))
                                .set("item", p_332590_.remove("Slot"))
                    );
                if (!list.isEmpty()) {
                    p_330721_.setComponent("minecraft:container", p_331427_.createList(list.stream()));
                }

                yield p_331427_.remove("Items");
            }
            case "minecraft:beehive" -> {
                p_330721_.setComponent("minecraft:bees", p_331427_.get("bees"));
                yield p_331427_.remove("bees");
            }
            default -> p_331427_;
        };
    }

    private static void fixEnchantments(
        ItemStackComponentizationFix.ItemStackData p_331903_, Dynamic<?> p_330744_, String p_331654_, String p_331804_, boolean p_331955_
    ) {
        OptionalDynamic<?> optionaldynamic = p_331903_.removeTag(p_331654_);
        List<Pair<String, Integer>> list = optionaldynamic.asList(Function.identity())
            .stream()
            .flatMap(p_330659_ -> parseEnchantment((Dynamic<?>)p_330659_).stream())
            .toList();
        if (!list.isEmpty() || p_331955_) {
            Dynamic<?> dynamic = p_330744_.emptyMap();
            Dynamic<?> dynamic1 = p_330744_.emptyMap();

            for (Pair<String, Integer> pair : list) {
                dynamic1 = dynamic1.set(pair.getFirst(), p_330744_.createInt(pair.getSecond()));
            }

            dynamic = dynamic.set("levels", dynamic1);
            if (p_331955_) {
                dynamic = dynamic.set("show_in_tooltip", p_330744_.createBoolean(false));
            }

            p_331903_.setComponent(p_331804_, dynamic);
        }

        if (optionaldynamic.result().isPresent() && list.isEmpty()) {
            p_331903_.setComponent("minecraft:enchantment_glint_override", p_330744_.createBoolean(true));
        }
    }

    private static Optional<Pair<String, Integer>> parseEnchantment(Dynamic<?> p_332205_) {
        return p_332205_.get("id")
            .asString()
            .apply2stable((p_331946_, p_330581_) -> Pair.of(p_331946_, Mth.clamp(p_330581_.intValue(), 0, 255)), p_332205_.get("lvl").asNumber())
            .result();
    }

    private static void fixAdventureModeChecks(ItemStackComponentizationFix.ItemStackData p_332034_, Dynamic<?> p_332091_, int p_331042_) {
        fixBlockStatePredicates(p_332034_, p_332091_, "CanDestroy", "minecraft:can_break", (p_331042_ & 8) != 0);
        fixBlockStatePredicates(p_332034_, p_332091_, "CanPlaceOn", "minecraft:can_place_on", (p_331042_ & 16) != 0);
    }

    private static void fixBlockStatePredicates(
        ItemStackComponentizationFix.ItemStackData p_331487_, Dynamic<?> p_331061_, String p_330321_, String p_332110_, boolean p_332166_
    ) {
        Optional<? extends Dynamic<?>> optional = p_331487_.removeTag(p_330321_).result();
        if (!optional.isEmpty()) {
            Dynamic<?> dynamic = p_331061_.emptyMap()
                .set(
                    "predicates",
                    p_331061_.createList(
                        optional.get()
                            .asStream()
                            .map(
                                p_337638_ -> DataFixUtils.orElse(
                                        p_337638_.asString().map(p_330959_ -> fixBlockStatePredicate((Dynamic<?>)p_337638_, p_330959_)).result(), p_337638_
                                    )
                            )
                    )
                );
            if (p_332166_) {
                dynamic = dynamic.set("show_in_tooltip", p_331061_.createBoolean(false));
            }

            p_331487_.setComponent(p_332110_, dynamic);
        }
    }

    private static Dynamic<?> fixBlockStatePredicate(Dynamic<?> p_331862_, String p_332123_) {
        int i = p_332123_.indexOf(91);
        int j = p_332123_.indexOf(123);
        int k = p_332123_.length();
        if (i != -1) {
            k = i;
        }

        if (j != -1) {
            k = Math.min(k, j);
        }

        String s = p_332123_.substring(0, k);
        Dynamic<?> dynamic = p_331862_.emptyMap().set("blocks", p_331862_.createString(s.trim()));
        int l = p_332123_.indexOf(93);
        if (i != -1 && l != -1) {
            Dynamic<?> dynamic1 = p_331862_.emptyMap();

            for (String s1 : PROPERTY_SPLITTER.split(p_332123_.substring(i + 1, l))) {
                int i1 = s1.indexOf(61);
                if (i1 != -1) {
                    String s2 = s1.substring(0, i1).trim();
                    String s3 = s1.substring(i1 + 1).trim();
                    dynamic1 = dynamic1.set(s2, p_331862_.createString(s3));
                }
            }

            dynamic = dynamic.set("state", dynamic1);
        }

        int j1 = p_332123_.indexOf(125);
        if (j != -1 && j1 != -1) {
            dynamic = dynamic.set("nbt", p_331862_.createString(p_332123_.substring(j, j1 + 1)));
        }

        return dynamic;
    }

    private static void fixAttributeModifiers(ItemStackComponentizationFix.ItemStackData p_330771_, Dynamic<?> p_331387_, int p_330887_) {
        OptionalDynamic<?> optionaldynamic = p_330771_.removeTag("AttributeModifiers");
        if (!optionaldynamic.result().isEmpty()) {
            boolean flag = (p_330887_ & 2) != 0;
            List<? extends Dynamic<?>> list = optionaldynamic.asList(ItemStackComponentizationFix::fixAttributeModifier);
            Dynamic<?> dynamic = p_331387_.emptyMap().set("modifiers", p_331387_.createList(list.stream()));
            if (flag) {
                dynamic = dynamic.set("show_in_tooltip", p_331387_.createBoolean(false));
            }

            p_330771_.setComponent("minecraft:attribute_modifiers", dynamic);
        }
    }

    private static Dynamic<?> fixAttributeModifier(Dynamic<?> p_331035_) {
        Dynamic<?> dynamic = p_331035_.emptyMap()
            .set("name", p_331035_.createString(""))
            .set("amount", p_331035_.createDouble(0.0))
            .set("operation", p_331035_.createString("add_value"));
        dynamic = Dynamic.copyField(p_331035_, "AttributeName", dynamic, "type");
        dynamic = Dynamic.copyField(p_331035_, "Slot", dynamic, "slot");
        dynamic = Dynamic.copyField(p_331035_, "UUID", dynamic, "uuid");
        dynamic = Dynamic.copyField(p_331035_, "Name", dynamic, "name");
        dynamic = Dynamic.copyField(p_331035_, "Amount", dynamic, "amount");
        return Dynamic.copyAndFixField(p_331035_, "Operation", dynamic, "operation", p_330453_ -> {
            return p_330453_.createString(switch (p_330453_.asInt(0)) {
                case 1 -> "add_multiplied_base";
                case 2 -> "add_multiplied_total";
                default -> "add_value";
            });
        });
    }

    private static Pair<Dynamic<?>, Dynamic<?>> fixMapDecoration(Dynamic<?> p_332095_) {
        Dynamic<?> dynamic = DataFixUtils.orElseGet(p_332095_.get("id").result(), () -> p_332095_.createString(""));
        Dynamic<?> dynamic1 = p_332095_.emptyMap()
            .set("type", p_332095_.createString(fixMapDecorationType(p_332095_.get("type").asInt(0))))
            .set("x", p_332095_.createDouble(p_332095_.get("x").asDouble(0.0)))
            .set("z", p_332095_.createDouble(p_332095_.get("z").asDouble(0.0)))
            .set("rotation", p_332095_.createFloat((float)p_332095_.get("rot").asDouble(0.0)));
        return Pair.of(dynamic, dynamic1);
    }

    private static String fixMapDecorationType(int p_330596_) {
        return switch (p_330596_) {
            case 1 -> "frame";
            case 2 -> "red_marker";
            case 3 -> "blue_marker";
            case 4 -> "target_x";
            case 5 -> "target_point";
            case 6 -> "player_off_map";
            case 7 -> "player_off_limits";
            case 8 -> "mansion";
            case 9 -> "monument";
            case 10 -> "banner_white";
            case 11 -> "banner_orange";
            case 12 -> "banner_magenta";
            case 13 -> "banner_light_blue";
            case 14 -> "banner_yellow";
            case 15 -> "banner_lime";
            case 16 -> "banner_pink";
            case 17 -> "banner_gray";
            case 18 -> "banner_light_gray";
            case 19 -> "banner_cyan";
            case 20 -> "banner_purple";
            case 21 -> "banner_blue";
            case 22 -> "banner_brown";
            case 23 -> "banner_green";
            case 24 -> "banner_red";
            case 25 -> "banner_black";
            case 26 -> "red_x";
            case 27 -> "village_desert";
            case 28 -> "village_plains";
            case 29 -> "village_savanna";
            case 30 -> "village_snowy";
            case 31 -> "village_taiga";
            case 32 -> "jungle_temple";
            case 33 -> "swamp_hut";
            default -> "player";
        };
    }

    private static void fixPotionContents(ItemStackComponentizationFix.ItemStackData p_330477_, Dynamic<?> p_330723_) {
        Dynamic<?> dynamic = p_330723_.emptyMap();
        Optional<String> optional = p_330477_.removeTag("Potion").asString().result().filter(p_330426_ -> !p_330426_.equals("minecraft:empty"));
        if (optional.isPresent()) {
            dynamic = dynamic.set("potion", p_330723_.createString(optional.get()));
        }

        dynamic = p_330477_.moveTagInto("CustomPotionColor", dynamic, "custom_color");
        dynamic = p_330477_.moveTagInto("custom_potion_effects", dynamic, "custom_effects");
        if (!dynamic.equals(p_330723_.emptyMap())) {
            p_330477_.setComponent("minecraft:potion_contents", dynamic);
        }
    }

    private static void fixWritableBook(ItemStackComponentizationFix.ItemStackData p_330306_, Dynamic<?> p_331455_) {
        Dynamic<?> dynamic = fixBookPages(p_330306_, p_331455_);
        if (dynamic != null) {
            p_330306_.setComponent("minecraft:writable_book_content", p_331455_.emptyMap().set("pages", dynamic));
        }
    }

    private static void fixWrittenBook(ItemStackComponentizationFix.ItemStackData p_330209_, Dynamic<?> p_332113_) {
        Dynamic<?> dynamic = fixBookPages(p_330209_, p_332113_);
        String s = p_330209_.removeTag("title").asString("");
        Optional<String> optional = p_330209_.removeTag("filtered_title").asString().result();
        Dynamic<?> dynamic1 = p_332113_.emptyMap();
        dynamic1 = dynamic1.set("title", createFilteredText(p_332113_, s, optional));
        dynamic1 = p_330209_.moveTagInto("author", dynamic1, "author");
        dynamic1 = p_330209_.moveTagInto("resolved", dynamic1, "resolved");
        dynamic1 = p_330209_.moveTagInto("generation", dynamic1, "generation");
        if (dynamic != null) {
            dynamic1 = dynamic1.set("pages", dynamic);
        }

        p_330209_.setComponent("minecraft:written_book_content", dynamic1);
    }

    @Nullable
    private static Dynamic<?> fixBookPages(ItemStackComponentizationFix.ItemStackData p_332071_, Dynamic<?> p_330407_) {
        List<String> list = p_332071_.removeTag("pages").asList(p_331677_ -> p_331677_.asString(""));
        Map<String, String> map = p_332071_.removeTag("filtered_pages").asMap(p_332151_ -> p_332151_.asString("0"), p_330471_ -> p_330471_.asString(""));
        if (list.isEmpty()) {
            return null;
        } else {
            List<Dynamic<?>> list1 = new ArrayList<>(list.size());

            for (int i = 0; i < list.size(); i++) {
                String s = list.get(i);
                String s1 = map.get(String.valueOf(i));
                list1.add(createFilteredText(p_330407_, s, Optional.ofNullable(s1)));
            }

            return p_330407_.createList(list1.stream());
        }
    }

    private static Dynamic<?> createFilteredText(Dynamic<?> p_331589_, String p_330423_, Optional<String> p_330385_) {
        Dynamic<?> dynamic = p_331589_.emptyMap().set("raw", p_331589_.createString(p_330423_));
        if (p_330385_.isPresent()) {
            dynamic = dynamic.set("filtered", p_331589_.createString(p_330385_.get()));
        }

        return dynamic;
    }

    private static void fixBucketedMobData(ItemStackComponentizationFix.ItemStackData p_331570_, Dynamic<?> p_330855_) {
        Dynamic<?> dynamic = p_330855_.emptyMap();

        for (String s : BUCKETED_MOB_TAGS) {
            dynamic = p_331570_.moveTagInto(s, dynamic, s);
        }

        if (!dynamic.equals(p_330855_.emptyMap())) {
            p_331570_.setComponent("minecraft:bucket_entity_data", dynamic);
        }
    }

    private static void fixLodestoneTracker(ItemStackComponentizationFix.ItemStackData p_332824_, Dynamic<?> p_332755_) {
        Optional<? extends Dynamic<?>> optional = p_332824_.removeTag("LodestonePos").result();
        Optional<? extends Dynamic<?>> optional1 = p_332824_.removeTag("LodestoneDimension").result();
        if (!optional.isEmpty() || !optional1.isEmpty()) {
            boolean flag = p_332824_.removeTag("LodestoneTracked").asBoolean(true);
            Dynamic<?> dynamic = p_332755_.emptyMap();
            if (optional.isPresent() && optional1.isPresent()) {
                dynamic = dynamic.set("target", p_332755_.emptyMap().set("pos", (Dynamic<?>)optional.get()).set("dimension", (Dynamic<?>)optional1.get()));
            }

            if (!flag) {
                dynamic = dynamic.set("tracked", p_332755_.createBoolean(false));
            }

            p_332824_.setComponent("minecraft:lodestone_tracker", dynamic);
        }
    }

    private static void fixFireworkStar(ItemStackComponentizationFix.ItemStackData p_330447_) {
        p_330447_.fixSubTag("Explosion", true, p_331995_ -> {
            p_330447_.setComponent("minecraft:firework_explosion", fixFireworkExplosion(p_331995_));
            return p_331995_.remove("Type").remove("Colors").remove("FadeColors").remove("Trail").remove("Flicker");
        });
    }

    private static void fixFireworkRocket(ItemStackComponentizationFix.ItemStackData p_330858_) {
        p_330858_.fixSubTag(
            "Fireworks",
            true,
            p_331577_ -> {
                Stream<? extends Dynamic<?>> stream = p_331577_.get("Explosions").asStream().map(ItemStackComponentizationFix::fixFireworkExplosion);
                int i = p_331577_.get("Flight").asInt(0);
                p_330858_.setComponent(
                    "minecraft:fireworks",
                    p_331577_.emptyMap().set("explosions", p_331577_.createList(stream)).set("flight_duration", p_331577_.createByte((byte)i))
                );
                return p_331577_.remove("Explosions").remove("Flight");
            }
        );
    }

    private static Dynamic<?> fixFireworkExplosion(Dynamic<?> p_332063_) {
        p_332063_ = p_332063_.set("shape", p_332063_.createString(switch (p_332063_.get("Type").asInt(0)) {
            case 1 -> "large_ball";
            case 2 -> "star";
            case 3 -> "creeper";
            case 4 -> "burst";
            default -> "small_ball";
        })).remove("Type");
        p_332063_ = p_332063_.renameField("Colors", "colors");
        p_332063_ = p_332063_.renameField("FadeColors", "fade_colors");
        p_332063_ = p_332063_.renameField("Trail", "has_trail");
        return p_332063_.renameField("Flicker", "has_twinkle");
    }

    public static Dynamic<?> fixProfile(Dynamic<?> p_330375_) {
        Optional<String> optional = p_330375_.asString().result();
        if (optional.isPresent()) {
            return isValidPlayerName(optional.get()) ? p_330375_.emptyMap().set("name", p_330375_.createString(optional.get())) : p_330375_.emptyMap();
        } else {
            String s = p_330375_.get("Name").asString("");
            Optional<? extends Dynamic<?>> optional1 = p_330375_.get("Id").result();
            Dynamic<?> dynamic = fixProfileProperties(p_330375_.get("Properties"));
            Dynamic<?> dynamic1 = p_330375_.emptyMap();
            if (isValidPlayerName(s)) {
                dynamic1 = dynamic1.set("name", p_330375_.createString(s));
            }

            if (optional1.isPresent()) {
                dynamic1 = dynamic1.set("id", (Dynamic<?>)optional1.get());
            }

            if (dynamic != null) {
                dynamic1 = dynamic1.set("properties", dynamic);
            }

            return dynamic1;
        }
    }

    private static boolean isValidPlayerName(String p_332666_) {
        return p_332666_.length() > 16 ? false : p_332666_.chars().filter(p_332597_ -> p_332597_ <= 32 || p_332597_ >= 127).findAny().isEmpty();
    }

    @Nullable
    private static Dynamic<?> fixProfileProperties(OptionalDynamic<?> p_330875_) {
        Map<String, List<Pair<String, Optional<String>>>> map = p_330875_.asMap(
            p_331855_ -> p_331855_.asString(""), p_331384_ -> p_331384_.asList(p_337640_ -> {
                    String s = p_337640_.get("Value").asString("");
                    Optional<String> optional = p_337640_.get("Signature").asString().result();
                    return Pair.of(s, optional);
                })
        );
        return map.isEmpty()
            ? null
            : p_330875_.createList(
                map.entrySet()
                    .stream()
                    .flatMap(
                        p_331925_ -> p_331925_.getValue()
                                .stream()
                                .map(
                                    p_331949_ -> {
                                        Dynamic<?> dynamic = p_330875_.emptyMap()
                                            .set("name", p_330875_.createString(p_331925_.getKey()))
                                            .set("value", p_330875_.createString(p_331949_.getFirst()));
                                        Optional<String> optional = p_331949_.getSecond();
                                        return optional.isPresent() ? dynamic.set("signature", p_330875_.createString(optional.get())) : dynamic;
                                    }
                                )
                    )
            );
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.writeFixAndRead(
            "ItemStack componentization",
            this.getInputSchema().getType(References.ITEM_STACK),
            this.getOutputSchema().getType(References.ITEM_STACK),
            p_331180_ -> {
                Optional<? extends Dynamic<?>> optional = ItemStackComponentizationFix.ItemStackData.read(p_331180_).map(p_330696_ -> {
                    fixItemStack(p_330696_, p_330696_.tag);
                    return p_330696_.write();
                });
                return DataFixUtils.orElse(optional, p_331180_);
            }
        );
    }

    static class ItemStackData {
        private final String item;
        private final int count;
        private Dynamic<?> components;
        private final Dynamic<?> remainder;
        Dynamic<?> tag;

        private ItemStackData(String p_330523_, int p_331147_, Dynamic<?> p_331630_) {
            this.item = NamespacedSchema.ensureNamespaced(p_330523_);
            this.count = p_331147_;
            this.components = p_331630_.emptyMap();
            this.tag = p_331630_.get("tag").orElseEmptyMap();
            this.remainder = p_331630_.remove("tag");
        }

        public static Optional<ItemStackComponentizationFix.ItemStackData> read(Dynamic<?> p_330273_) {
            return p_330273_.get("id")
                .asString()
                .apply2stable(
                    (p_331191_, p_330701_) -> new ItemStackComponentizationFix.ItemStackData(
                            p_331191_, p_330701_.intValue(), p_330273_.remove("id").remove("Count")
                        ),
                    p_330273_.get("Count").asNumber()
                )
                .result();
        }

        public OptionalDynamic<?> removeTag(String p_330602_) {
            OptionalDynamic<?> optionaldynamic = this.tag.get(p_330602_);
            this.tag = this.tag.remove(p_330602_);
            return optionaldynamic;
        }

        public void setComponent(String p_330861_, Dynamic<?> p_331399_) {
            this.components = this.components.set(p_330861_, p_331399_);
        }

        public void setComponent(String p_331000_, OptionalDynamic<?> p_332145_) {
            p_332145_.result().ifPresent(p_332105_ -> this.components = this.components.set(p_331000_, (Dynamic<?>)p_332105_));
        }

        public Dynamic<?> moveTagInto(String p_330240_, Dynamic<?> p_330972_, String p_331818_) {
            Optional<? extends Dynamic<?>> optional = this.removeTag(p_330240_).result();
            return optional.isPresent() ? p_330972_.set(p_331818_, (Dynamic<?>)optional.get()) : p_330972_;
        }

        public void moveTagToComponent(String p_331198_, String p_330485_, Dynamic<?> p_330981_) {
            Optional<? extends Dynamic<?>> optional = this.removeTag(p_331198_).result();
            if (optional.isPresent() && !optional.get().equals(p_330981_)) {
                this.setComponent(p_330485_, (Dynamic<?>)optional.get());
            }
        }

        public void moveTagToComponent(String p_330961_, String p_330509_) {
            this.removeTag(p_330961_).result().ifPresent(p_330514_ -> this.setComponent(p_330509_, (Dynamic<?>)p_330514_));
        }

        public void fixSubTag(String p_330776_, boolean p_330566_, UnaryOperator<Dynamic<?>> p_330246_) {
            OptionalDynamic<?> optionaldynamic = this.tag.get(p_330776_);
            if (!p_330566_ || !optionaldynamic.result().isEmpty()) {
                Dynamic<?> dynamic = optionaldynamic.orElseEmptyMap();
                dynamic = p_330246_.apply(dynamic);
                if (dynamic.equals(dynamic.emptyMap())) {
                    this.tag = this.tag.remove(p_330776_);
                } else {
                    this.tag = this.tag.set(p_330776_, dynamic);
                }
            }
        }

        public Dynamic<?> write() {
            Dynamic<?> dynamic = this.tag.emptyMap().set("id", this.tag.createString(this.item)).set("count", this.tag.createInt(this.count));
            if (!this.tag.equals(this.tag.emptyMap())) {
                this.components = this.components.set("minecraft:custom_data", this.tag);
            }

            if (!this.components.equals(this.tag.emptyMap())) {
                dynamic = dynamic.set("components", this.components);
            }

            return mergeRemainder(dynamic, this.remainder);
        }

        private static <T> Dynamic<T> mergeRemainder(Dynamic<T> p_331175_, Dynamic<?> p_330435_) {
            DynamicOps<T> dynamicops = p_331175_.getOps();
            return dynamicops.getMap(p_331175_.getValue())
                .flatMap(p_330670_ -> dynamicops.mergeToMap(p_330435_.convert(dynamicops).getValue(), (MapLike<T>)p_330670_))
                .map(p_331482_ -> new Dynamic<>(dynamicops, (T)p_331482_))
                .result()
                .orElse(p_331175_);
        }

        public boolean is(String p_330700_) {
            return this.item.equals(p_330700_);
        }

        public boolean is(Set<String> p_330784_) {
            return p_330784_.contains(this.item);
        }

        public boolean hasComponent(String p_332677_) {
            return this.components.get(p_332677_).result().isPresent();
        }
    }
}
