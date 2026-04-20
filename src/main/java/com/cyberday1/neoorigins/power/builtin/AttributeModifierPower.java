package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public class AttributeModifierPower extends PowerType<AttributeModifierPower.Config> {

    public record Config(
        Identifier attribute,
        double amount,
        AttributeModifier.Operation operation,
        EntityCondition condition,   // alwaysTrue() when absent — preserves pre-2.0 behaviour
        boolean hasCondition,        // marker so onTick knows to edge-trigger
        String type
    ) implements PowerConfiguration {

        private static AttributeModifier.Operation parseOp(String s) {
            return switch (s) {
                case "add_value" -> AttributeModifier.Operation.ADD_VALUE;
                case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                default -> AttributeModifier.Operation.ADD_VALUE;
            };
        }

        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json;
                try {
                    json = ops.convertTo(JsonOps.INSTANCE, input);
                } catch (Exception e) {
                    return DataResult.error(() -> "attribute_modifier: could not convert to JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "attribute_modifier: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();
                if (!obj.has("attribute") || !obj.has("amount")) {
                    return DataResult.error(() -> "attribute_modifier: missing required fields (attribute, amount)");
                }
                Identifier attr = Identifier.parse(obj.get("attribute").getAsString());
                double amount = obj.get("amount").getAsDouble();
                AttributeModifier.Operation op = obj.has("operation")
                    ? parseOp(obj.get("operation").getAsString())
                    : AttributeModifier.Operation.ADD_VALUE;
                String t = obj.has("type") ? obj.get("type").getAsString() : "";
                boolean hasCond = obj.has("condition") && obj.get("condition").isJsonObject();
                EntityCondition cond = hasCond
                    ? ConditionParser.parse(obj.getAsJsonObject("condition"), t)
                    : EntityCondition.alwaysTrue();
                return DataResult.success(Pair.of(
                    new Config(attr, amount, op, cond, hasCond, t),
                    ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(prefix);
            }
        };
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        // If gated by a condition, only apply when the condition is currently true.
        if (config.hasCondition() && !config.condition().test(player)) return;
        applyModifier(player, config, true);
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        applyModifier(player, config, false);
    }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (!config.hasCondition()) return;
        // Edge-triggered add/remove: only mutate the attribute on state change.
        boolean shouldBeActive = config.condition().test(player);
        boolean isActive = hasActiveModifier(player, config);
        if (shouldBeActive && !isActive) applyModifier(player, config, true);
        else if (!shouldBeActive && isActive) applyModifier(player, config, false);
    }

    private boolean hasActiveModifier(ServerPlayer player, Config config) {
        Identifier attrId = config.attribute();
        var attrHolderOpt = BuiltInRegistries.ATTRIBUTE.get(attrId);
        if (attrHolderOpt.isEmpty()) {
            Identifier p = Identifier.fromNamespaceAndPath(attrId.getNamespace(), "generic." + attrId.getPath());
            attrHolderOpt = BuiltInRegistries.ATTRIBUTE.get(p);
            if (attrHolderOpt.isPresent()) attrId = p;
        }
        if (attrHolderOpt.isEmpty()) {
            Identifier p = Identifier.fromNamespaceAndPath(attrId.getNamespace(), "player." + attrId.getPath());
            attrHolderOpt = BuiltInRegistries.ATTRIBUTE.get(p);
            if (attrHolderOpt.isPresent()) attrId = p;
        }
        if (attrHolderOpt.isEmpty()) return false;
        AttributeInstance instance = player.getAttribute(attrHolderOpt.get());
        if (instance == null) return false;
        return instance.getModifier(modIdFor(attrId, config)) != null;
    }

    private Identifier modIdFor(Identifier attrId, Config config) {
        // Include amount+op hash so two attribute_modifier powers on the same
        // attribute (e.g. speed +0.1 + speed +0.2) don't collide on the same modId.
        int h = java.util.Objects.hash(config.amount(), config.operation());
        return Identifier.fromNamespaceAndPath(
            "neoorigins",
            "power_" + attrId.getPath() + "_" + Integer.toHexString(h));
    }

    private void applyModifier(ServerPlayer player, Config config, boolean add) {
        // MC 26.1 still registers attributes with "generic." / "player." prefixes
        // (e.g. minecraft:generic.fall_damage_multiplier). Powers author IDs without
        // the prefix for portability, so try the ID as given and fall back to
        // prefixed variants. The previous "strip generic." logic only worked if
        // callers had already written the prefixed form.
        Identifier attrId = config.attribute();
        var attrHolderOpt = BuiltInRegistries.ATTRIBUTE.get(attrId);
        if (attrHolderOpt.isEmpty()) {
            Identifier prefixed = Identifier.fromNamespaceAndPath(attrId.getNamespace(), "generic." + attrId.getPath());
            attrHolderOpt = BuiltInRegistries.ATTRIBUTE.get(prefixed);
            if (attrHolderOpt.isPresent()) attrId = prefixed;
        }
        if (attrHolderOpt.isEmpty()) {
            Identifier prefixed = Identifier.fromNamespaceAndPath(attrId.getNamespace(), "player." + attrId.getPath());
            attrHolderOpt = BuiltInRegistries.ATTRIBUTE.get(prefixed);
            if (attrHolderOpt.isPresent()) attrId = prefixed;
        }
        if (attrHolderOpt.isEmpty()) return;
        var attrHolder = attrHolderOpt.get();
        AttributeInstance instance = player.getAttribute(attrHolder);
        if (instance == null) return;

        Identifier modId = modIdFor(attrId, config);
        if (add) {
            if (instance.getModifier(modId) == null) {
                instance.addPermanentModifier(new AttributeModifier(modId, config.amount(), config.operation()));
            }
        } else {
            instance.removeModifier(modId);
        }
    }
}
