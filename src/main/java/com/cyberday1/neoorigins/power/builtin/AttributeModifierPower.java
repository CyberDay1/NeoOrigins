package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.condition.LocationCondition;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public class AttributeModifierPower extends PowerType<AttributeModifierPower.Config> {

    /**
     * Equipment-slot predicate: active when the stack worn in {@code slot} matches
     * either the given {@code item} ID or the given {@code tag}. At least one of
     * {@code item}/{@code tag} must be present; if both are supplied, either match
     * satisfies the condition.
     */
    public record EquipmentCondition(
        String slot,
        Optional<Identifier> item,
        Optional<Identifier> tag
    ) {
        public static final Codec<EquipmentCondition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("slot").forGetter(EquipmentCondition::slot),
            Identifier.CODEC.optionalFieldOf("item").forGetter(EquipmentCondition::item),
            Identifier.CODEC.optionalFieldOf("tag").forGetter(EquipmentCondition::tag)
        ).apply(inst, EquipmentCondition::new));
    }

    public record Config(
        Identifier attribute,
        double amount,
        AttributeModifier.Operation operation,
        Optional<String> condition,
        Optional<EquipmentCondition> equipmentCondition,
        Optional<LocationCondition> locationCondition,
        String type
    ) implements PowerConfiguration {

        private static final Codec<AttributeModifier.Operation> OPERATION_CODEC = Codec.STRING.xmap(
            s -> switch (s) {
                case "add_value" -> AttributeModifier.Operation.ADD_VALUE;
                case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                default -> AttributeModifier.Operation.ADD_VALUE;
            },
            op -> switch (op) {
                case ADD_VALUE -> "add_value";
                case ADD_MULTIPLIED_BASE -> "add_multiplied_base";
                case ADD_MULTIPLIED_TOTAL -> "add_multiplied_total";
            }
        );

        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.fieldOf("attribute").forGetter(Config::attribute),
            Codec.DOUBLE.fieldOf("amount").forGetter(Config::amount),
            OPERATION_CODEC.optionalFieldOf("operation", AttributeModifier.Operation.ADD_VALUE).forGetter(Config::operation),
            Codec.STRING.optionalFieldOf("condition").forGetter(Config::condition),
            EquipmentCondition.CODEC.optionalFieldOf("equipment_condition").forGetter(Config::equipmentCondition),
            LocationCondition.CODEC.optionalFieldOf("location_condition").forGetter(Config::locationCondition),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        // Unconditional powers apply immediately; conditional ones are driven by onTick.
        if (isUnconditional(config)) {
            applyModifier(player, config, true);
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        applyModifier(player, config, false);
    }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (isUnconditional(config)) return;
        if (player.tickCount % 5 != 0) return;
        boolean shouldApply = true;
        if (config.condition().isPresent()) {
            shouldApply = evaluate(config.condition().get(), player);
        }
        if (shouldApply && config.equipmentCondition().isPresent()) {
            shouldApply = evaluateEquipment(config.equipmentCondition().get(), player);
        }
        if (shouldApply && config.locationCondition().isPresent()) {
            shouldApply = config.locationCondition().get().test(player);
        }
        applyModifier(player, config, shouldApply);
    }

    private static boolean isUnconditional(Config config) {
        return config.condition().isEmpty()
            && config.equipmentCondition().isEmpty()
            && config.locationCondition().isEmpty();
    }

    private static boolean evaluate(String condition, ServerPlayer player) {
        return switch (condition) {
            case "in_water" -> player.isInWater();
            case "on_land"  -> !player.isInWater();
            case "in_lava"  -> player.isInLava();
            default -> {
                NeoOrigins.LOGGER.warn("attribute_modifier condition '{}' is unknown — expected one of in_water, on_land, in_lava. Treating as always-on.", condition);
                yield true;
            }
        };
    }

    private static boolean evaluateEquipment(EquipmentCondition cond, ServerPlayer player) {
        EquipmentSlot slot;
        try {
            slot = EquipmentSlot.byName(cond.slot());
        } catch (IllegalArgumentException ex) {
            NeoOrigins.LOGGER.warn("attribute_modifier equipment_condition.slot '{}' is unknown — expected one of mainhand, offhand, head, chest, legs, feet, body.", cond.slot());
            return false;
        }
        ItemStack stack = player.getItemBySlot(slot);
        if (stack.isEmpty()) return false;

        if (cond.item().isPresent()) {
            var itemHolder = BuiltInRegistries.ITEM.get(cond.item().get());
            if (itemHolder.isPresent() && stack.is(itemHolder.get())) return true;
        }
        if (cond.tag().isPresent()) {
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, cond.tag().get());
            if (stack.is(tagKey)) return true;
        }
        // If neither item nor tag is supplied, treat as "any non-empty stack in this slot".
        return cond.item().isEmpty() && cond.tag().isEmpty();
    }

    private void applyModifier(ServerPlayer player, Config config, boolean add) {
        // Resolve the attribute ID. 1.21.1 uses prefixed names like
        // "generic.armor" / "player.block_interaction_range"; 26.1 dropped
        // the prefix entirely — attributes register as bare "armor",
        // "max_health", etc. Origin authors may JSON-write either form, so
        // probe both directions: strip the prefix if present, add one if
        // absent.
        final Identifier original = config.attribute();
        Identifier attrId = original;
        var attrOpt = BuiltInRegistries.ATTRIBUTE.get(attrId);
        if (attrOpt.isEmpty() && original.getPath().startsWith("generic.")) {
            attrId = Identifier.fromNamespaceAndPath(original.getNamespace(), original.getPath().substring("generic.".length()));
            attrOpt = BuiltInRegistries.ATTRIBUTE.get(attrId);
        }
        if (attrOpt.isEmpty() && original.getPath().startsWith("player.")) {
            attrId = Identifier.fromNamespaceAndPath(original.getNamespace(), original.getPath().substring("player.".length()));
            attrOpt = BuiltInRegistries.ATTRIBUTE.get(attrId);
        }
        if (attrOpt.isEmpty()) {
            attrId = Identifier.fromNamespaceAndPath(original.getNamespace(), "generic." + original.getPath());
            attrOpt = BuiltInRegistries.ATTRIBUTE.get(attrId);
        }
        if (attrOpt.isEmpty()) {
            attrId = Identifier.fromNamespaceAndPath(original.getNamespace(), "player." + original.getPath());
            attrOpt = BuiltInRegistries.ATTRIBUTE.get(attrId);
        }
        if (attrOpt.isEmpty()) {
            if (add) {
                NeoOrigins.LOGGER.warn(
                    "attribute_modifier power references unknown attribute '{}' — tried the ID as-is, with and without 'generic.' / 'player.' prefixes. Check the JSON.",
                    original);
            }
            return;
        }
        var attrHolder = attrOpt.get();
        AttributeInstance instance = player.getAttribute(attrHolder);
        if (instance == null) {
            if (add) {
                NeoOrigins.LOGGER.warn(
                    "attribute_modifier power references attribute '{}' which exists in the registry but is not attached to the player — no-op.",
                    attrId);
            }
            return;
        }

        Identifier modId = Identifier.fromNamespaceAndPath("neoorigins", "power_" + attrId.getPath());
        if (add) {
            if (instance.getModifier(modId) == null) {
                instance.addPermanentModifier(new AttributeModifier(modId, config.amount(), config.operation()));
            }
        } else {
            instance.removeModifier(modId);
        }
    }
}
