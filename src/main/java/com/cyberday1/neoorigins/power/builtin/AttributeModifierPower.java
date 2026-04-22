package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.condition.LocationCondition;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Scales an attribute while the player satisfies all configured gates.
 *
 * <p>Three optional gates, AND'd at tick time:
 * <ul>
 *   <li>{@code condition} — either a legacy shorthand string
 *       ({@code "in_water"}, {@code "on_land"}, {@code "in_lava"}) or a
 *       full origins/apoli condition DSL JSON object
 *       (e.g. {@code { "type": "origins:biome", ... }}).</li>
 *   <li>{@code equipment_condition} — matches a worn item by ID or tag
 *       in a given slot.</li>
 *   <li>{@code location_condition} — matches dimension / biome / biome tag /
 *       structure / structure tag.</li>
 * </ul>
 *
 * <p>Unconditional powers apply at {@code onGranted} and stay on for the
 * power's lifetime. Gated powers are edge-triggered every 5 ticks — the
 * modifier is added/removed only on state change.
 */
public class AttributeModifierPower extends PowerType<AttributeModifierPower.Config> {

    public record EquipmentCondition(
        String slot,
        Optional<ResourceLocation> item,
        Optional<ResourceLocation> tag
    ) {}

    public record Config(
        ResourceLocation attribute,
        double amount,
        AttributeModifier.Operation operation,
        Optional<EntityCondition> condition,
        Optional<EquipmentCondition> equipmentCondition,
        Optional<LocationCondition> locationCondition,
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

        private static Optional<EntityCondition> parseCondition(JsonElement el, String contextId) {
            if (el == null || el.isJsonNull()) return Optional.empty();
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                String s = el.getAsString();
                EntityCondition c = switch (s) {
                    case "in_water" -> p -> p.isInWater();
                    case "on_land"  -> p -> !p.isInWater();
                    case "in_lava"  -> p -> p.isInLava();
                    default -> {
                        NeoOrigins.LOGGER.warn(
                            "attribute_modifier condition string '{}' is unknown — expected one of in_water, on_land, in_lava, or use a DSL condition object. Treating as always-on.",
                            s);
                        yield p -> true;
                    }
                };
                return Optional.of(c);
            }
            if (el.isJsonObject()) {
                return Optional.of(ConditionParser.parse(el.getAsJsonObject(), contextId));
            }
            return Optional.empty();
        }

        private static Optional<EquipmentCondition> parseEquipment(JsonObject obj) {
            if (!obj.has("equipment_condition") || !obj.get("equipment_condition").isJsonObject()) {
                return Optional.empty();
            }
            JsonObject ec = obj.getAsJsonObject("equipment_condition");
            String slot = ec.has("slot") ? ec.get("slot").getAsString() : "mainhand";
            Optional<ResourceLocation> item = ec.has("item")
                ? Optional.of(ResourceLocation.parse(ec.get("item").getAsString()))
                : Optional.empty();
            Optional<ResourceLocation> tag = ec.has("tag")
                ? Optional.of(ResourceLocation.parse(ec.get("tag").getAsString()))
                : Optional.empty();
            return Optional.of(new EquipmentCondition(slot, item, tag));
        }

        private static Optional<LocationCondition> parseLocation(JsonObject obj) {
            if (!obj.has("location_condition") || !obj.get("location_condition").isJsonObject()) {
                return Optional.empty();
            }
            DataResult<LocationCondition> parsed = LocationCondition.CODEC.parse(
                JsonOps.INSTANCE, obj.get("location_condition"));
            return parsed.result();
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
                ResourceLocation attr = ResourceLocation.parse(obj.get("attribute").getAsString());
                double amount = obj.get("amount").getAsDouble();
                AttributeModifier.Operation op = obj.has("operation")
                    ? parseOp(obj.get("operation").getAsString())
                    : AttributeModifier.Operation.ADD_VALUE;
                String t = obj.has("type") ? obj.get("type").getAsString() : "";
                Optional<EntityCondition> cond = parseCondition(obj.get("condition"), t);
                Optional<EquipmentCondition> eq = parseEquipment(obj);
                Optional<LocationCondition> loc = parseLocation(obj);
                return DataResult.success(Pair.of(
                    new Config(attr, amount, op, cond, eq, loc, t),
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
        boolean shouldBeActive = evaluateAll(player, config);
        boolean isActive = hasActiveModifier(player, config);
        if (shouldBeActive && !isActive) applyModifier(player, config, true);
        else if (!shouldBeActive && isActive) applyModifier(player, config, false);
    }

    private static boolean isUnconditional(Config config) {
        return config.condition().isEmpty()
            && config.equipmentCondition().isEmpty()
            && config.locationCondition().isEmpty();
    }

    private static boolean evaluateAll(ServerPlayer player, Config config) {
        if (config.condition().isPresent() && !config.condition().get().test(player)) return false;
        if (config.equipmentCondition().isPresent() && !evaluateEquipment(config.equipmentCondition().get(), player)) return false;
        if (config.locationCondition().isPresent() && !config.locationCondition().get().test(player)) return false;
        return true;
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
            Item item = BuiltInRegistries.ITEM.get(cond.item().get());
            if (stack.is(item)) return true;
        }
        if (cond.tag().isPresent()) {
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, cond.tag().get());
            if (stack.is(tagKey)) return true;
        }
        return cond.item().isEmpty() && cond.tag().isEmpty();
    }

    private boolean hasActiveModifier(ServerPlayer player, Config config) {
        ResolvedAttribute resolved = resolveAttribute(config);
        if (resolved == null) return false;
        AttributeInstance instance = player.getAttribute(resolved.holder);
        if (instance == null) return false;
        return instance.getModifier(modIdFor(resolved.id, config)) != null;
    }

    private void applyModifier(ServerPlayer player, Config config, boolean add) {
        ResolvedAttribute resolved = resolveAttribute(config);
        if (resolved == null) {
            if (add) {
                NeoOrigins.LOGGER.warn(
                    "attribute_modifier power references unknown attribute '{}' — tried with no prefix, 'generic.', and 'player.' variants. Check the JSON.",
                    config.attribute());
            }
            return;
        }
        AttributeInstance instance = player.getAttribute(resolved.holder);
        if (instance == null) {
            if (add) {
                NeoOrigins.LOGGER.warn(
                    "attribute_modifier power references attribute '{}' which exists in the registry but is not attached to the player — no-op.",
                    resolved.id);
            }
            return;
        }
        ResourceLocation modId = modIdFor(resolved.id, config);
        if (add) {
            purgeStaleModifiers(instance, resolved.id, modId);
            if (instance.getModifier(modId) == null) {
                instance.addPermanentModifier(new AttributeModifier(modId, config.amount(), config.operation()));
            }
        } else {
            instance.removeModifier(modId);
        }
    }

    private static final java.util.Set<String> OPERATION_PREFIXES;
    static {
        java.util.Set<String> s = new java.util.HashSet<>();
        for (AttributeModifier.Operation op : AttributeModifier.Operation.values()) {
            s.add(op.name().toLowerCase(java.util.Locale.ROOT));
        }
        OPERATION_PREFIXES = java.util.Set.copyOf(s);
    }

    /**
     * Removes legacy-format modifiers for this attribute whose IDs predate the
     * JVM-stable hash fix. The old {@code modIdFor} hashed the operation enum,
     * whose {@code hashCode()} varies per-process, so every login generated a
     * fresh ID like {@code neoorigins:power_max_health_<unstable-hex>} while
     * NBT kept the previous session's modifier around — max_health stacked
     * indefinitely across relogs.
     *
     * <p>The new format is {@code power_<attrpath>_<opname>_<hex>}. Any
     * {@code power_<attrpath>_<hex>} (no opname between) is an old orphan and
     * gets removed. Other powers targeting the same attribute with different
     * amounts use their own {@code keep}-equivalent IDs and are left alone.
     */
    private static void purgeStaleModifiers(AttributeInstance instance, ResourceLocation attrId, ResourceLocation keep) {
        String prefix = "power_" + attrId.getPath() + "_";
        java.util.List<ResourceLocation> stale = new java.util.ArrayList<>();
        for (AttributeModifier m : instance.getModifiers()) {
            ResourceLocation id = m.id();
            if (!"neoorigins".equals(id.getNamespace())) continue;
            String path = id.getPath();
            if (!path.startsWith(prefix)) continue;
            if (id.equals(keep)) continue;

            String suffix = path.substring(prefix.length());
            int underscore = suffix.indexOf('_');
            if (underscore > 0 && OPERATION_PREFIXES.contains(suffix.substring(0, underscore))) {
                continue; // new-format modifier belonging to a different power
            }
            stale.add(id);
        }
        for (ResourceLocation id : stale) instance.removeModifier(id);
    }

    /** Resolves the attribute with prefix tolerance. 1.21.1 registers attributes
     *  with generic./player. prefixes; 26.1 registers them without. Try both
     *  directions so pack JSON is portable. */
    private ResolvedAttribute resolveAttribute(Config config) {
        ResourceLocation raw = config.attribute();
        var holder = BuiltInRegistries.ATTRIBUTE.getOptional(raw);
        if (holder.isPresent()) {
            return new ResolvedAttribute(raw, BuiltInRegistries.ATTRIBUTE.wrapAsHolder(holder.get()));
        }

        ResourceLocation withGeneric = ResourceLocation.fromNamespaceAndPath(raw.getNamespace(), "generic." + raw.getPath());
        holder = BuiltInRegistries.ATTRIBUTE.getOptional(withGeneric);
        if (holder.isPresent()) {
            return new ResolvedAttribute(withGeneric, BuiltInRegistries.ATTRIBUTE.wrapAsHolder(holder.get()));
        }

        ResourceLocation withPlayer = ResourceLocation.fromNamespaceAndPath(raw.getNamespace(), "player." + raw.getPath());
        holder = BuiltInRegistries.ATTRIBUTE.getOptional(withPlayer);
        if (holder.isPresent()) {
            return new ResolvedAttribute(withPlayer, BuiltInRegistries.ATTRIBUTE.wrapAsHolder(holder.get()));
        }

        String path = raw.getPath();
        if (path.startsWith("generic.") || path.startsWith("player.")) {
            ResourceLocation stripped = ResourceLocation.fromNamespaceAndPath(raw.getNamespace(),
                path.substring(path.indexOf('.') + 1));
            holder = BuiltInRegistries.ATTRIBUTE.getOptional(stripped);
            if (holder.isPresent()) {
                return new ResolvedAttribute(stripped, BuiltInRegistries.ATTRIBUTE.wrapAsHolder(holder.get()));
            }
        }
        return null;
    }

    private record ResolvedAttribute(ResourceLocation id, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> holder) {}

    private ResourceLocation modIdFor(ResourceLocation attrId, Config config) {
        // Only hash values with stable hashCode() across JVM restarts. Enum
        // hashCode() is identity-based and varied per-process, which made
        // every login generate a fresh modifier ID — the persisted NBT
        // modifier from the last session stuck around, the new one got
        // added alongside it, and max_health powers stacked indefinitely
        // across relogs (#tester).
        int h = Double.hashCode(config.amount());
        return ResourceLocation.fromNamespaceAndPath(
            "neoorigins",
            "power_" + attrId.getPath() + "_"
                + config.operation().name().toLowerCase(java.util.Locale.ROOT) + "_"
                + Integer.toHexString(h));
    }
}
