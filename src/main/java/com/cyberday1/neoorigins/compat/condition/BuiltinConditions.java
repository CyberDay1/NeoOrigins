package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.registry.ConditionType;
import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The built-in {@link ConditionType} descriptors — the registry refactor's
 * static, class-load-time source of truth for condition verbs that have been
 * migrated off the {@code ConditionParser} switch. Condition analogue of
 * {@link com.cyberday1.neoorigins.compat.action.BuiltinActions}; see that type
 * for the full rationale.
 *
 * <p><b>Why static, not just the NeoForge registry?</b> {@link com.cyberday1.neoorigins.compat.registry.CompatRegistries}
 * exposes the verb set via {@code conditionKeys()}, but that reads the live
 * registry which only populates after {@code NewRegistryEvent} fires — i.e. never
 * in the headless harnesses ({@code compatTest}, {@code goldenMaster},
 * {@code schemaFormCheck}). This table is available the moment the class loads,
 * with or without a running NeoForge, so it can back both the parser's dispatch
 * and {@code KNOWN_TYPES} auditing headlessly. At mod init
 * {@code CompatRegistries.register} copies every entry into the DeferredRegister,
 * so runtime lookups and addon contributions see the same descriptors through the
 * registry.
 *
 * <p>Migration is verb-by-verb (locked decision D1): each entry added here lets
 * its {@code case} arm be deleted from {@code ConditionParser}, gated on the
 * golden-master staying byte-identical and {@code SchemaFormCheck} green.
 */
public final class BuiltinConditions {

    private BuiltinConditions() {}

    /** Shared {@code comparison} ENUM field (vanilla operator vocabulary). */
    private static FieldSpec comparison(String defaultOp, String doc) {
        return new FieldSpec("comparison", FormFieldSpec.Kind.ENUM, false)
            .options("==", "!=", ">", ">=", "<", "<=")
            .def(defaultOp)
            .doc(doc);
    }

    /** Shared {@code compare_to} threshold field. */
    private static FieldSpec compareTo(FormFieldSpec.Kind kind, Object def, String doc) {
        return new FieldSpec("compare_to", kind, false).def(def).doc(doc);
    }

    /** Insertion-ordered so registration/audit output is deterministic. */
    private static final Map<ResourceLocation, ConditionType> DESCRIPTORS = new LinkedHashMap<>();
    /** Canonical {@code "neoorigins:<verb>"} string → descriptor, for hot-path dispatch. */
    private static final Map<String, ConditionType> BY_KEY = new java.util.HashMap<>();

    private static void define(String path, ConditionType.Factory factory, List<FieldSpec> fields) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, path);
        ConditionType type = new ConditionType(id, factory, fields);
        DESCRIPTORS.put(id, type);
        BY_KEY.put(id.toString(), type);
    }

    /**
     * Define an aliased descriptor: {@code path} is the canonical id; every entry
     * in {@code aliasPaths} dispatches to the same factory. Only the canonical id
     * is registered ({@link #DESCRIPTORS} / the live registry) and counted toward
     * the type total — the aliases are known-verb synonyms (lift-and-shift of a
     * multi-label {@code case "a", "b" ->} switch arm), routed through
     * {@link #BY_KEY} so {@code ConditionParser} dispatch accepts them verbatim,
     * and surfaced to {@code SchemaFormCheck} via {@link #aliasIds()} so the
     * {@code KNOWN_TYPES} parity check treats them as handled.
     */
    private static void define(String path, List<String> aliasPaths,
                               ConditionType.Factory factory, List<FieldSpec> fields) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, path);
        List<ResourceLocation> aliases = aliasPaths.stream()
            .map(p -> ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, p))
            .toList();
        ConditionType type = new ConditionType(id, factory, fields, aliases);
        DESCRIPTORS.put(id, type);
        BY_KEY.put(id.toString(), type);
        for (ResourceLocation alias : aliases) BY_KEY.put(alias.toString(), type);
    }

    static {
        // Descriptors are migrated here verb-by-verb off the ConditionParser
        // switch (locked decision D1). Each is the lift-and-shift of the old case
        // body; the parser dispatches through BuiltinConditions for migrated verbs.

        // ---- Zero-config marker conditions (no JSON fields read) ----
        // sneaking — player is crouching.
        define("sneaking", (json, ctx) -> p -> p.isShiftKeyDown(), List.of());
        // sprinting — player is sprinting.
        define("sprinting", (json, ctx) -> p -> p.isSprinting(), List.of());
        // on_ground — player is standing on ground.
        define("on_ground", (json, ctx) -> p -> p.onGround(), List.of());
        // in_water — player is in a water column.
        define("in_water", (json, ctx) -> p -> p.isInWater(), List.of());
        // swimming — player is in the swimming pose.
        define("swimming", (json, ctx) -> p -> p.isSwimming(), List.of());
        // submerged_in_water — player's eyes are underwater.
        define("submerged_in_water", (json, ctx) -> p -> p.isUnderWater(), List.of());
        // fall_flying — player is gliding with an elytra.
        define("fall_flying", (json, ctx) -> p -> p.isFallFlying(), List.of());
        // invisible — player has the invisibility flag set.
        define("invisible", (json, ctx) -> p -> p.isInvisible(), List.of());
        // using_item — player is actively using/charging an item.
        define("using_item", (json, ctx) -> p -> p.isUsingItem(), List.of());
        // ticking — player entity has not been removed.
        define("ticking", (json, ctx) -> p -> !p.isRemoved(), List.of());
        // exists — player is present and not removed.
        define("exists", (json, ctx) -> p -> p != null && !p.isRemoved(), List.of());
        // living — player is alive.
        define("living", (json, ctx) -> p -> p.isAlive(), List.of());
        // creative_flying — player's flying ability is active.
        define("creative_flying", (json, ctx) -> p -> p.getAbilities().flying, List.of());
        // climbing — player is on a climbable block.
        define("climbing", (json, ctx) -> p -> p.onClimbable(), List.of());
        // moving — player has nonzero horizontal delta movement.
        define("moving", (json, ctx) -> p -> {
            var dm = p.getDeltaMovement();
            return dm.x != 0 || dm.z != 0;
        }, List.of());
        // passenger / riding — player is riding a vehicle. `riding` is a synonym.
        define("passenger", List.of("riding"), (json, ctx) -> p -> p.isPassenger(), List.of());
        // on_fire / fire — player is on fire. `fire` is a synonym.
        define("on_fire", List.of("fire"), (json, ctx) -> p -> p.isOnFire(), List.of());

        // ---- World / time / weather conditions (read live world state) ----
        // constant — literal boolean. `value` optional (absent → false).
        define("constant",
            (json, ctx) -> json.has("value") && json.get("value").getAsBoolean()
                ? EntityCondition.alwaysTrue() : EntityCondition.alwaysFalse(),
            List.of(new FieldSpec("value", FormFieldSpec.Kind.BOOLEAN, false)
                .def(false)
                .doc("Constant result this condition always returns (default false).")));
        // block_collision — always true (placeholder; no spatial query implemented).
        define("block_collision", (json, ctx) -> EntityCondition.alwaysTrue(), List.of());
        // daytime — vanilla day window (0–13000 of the 24000-tick day).
        define("daytime",
            (json, ctx) -> p -> p.level().getDayTime() % 24000L < 13000L, List.of());
        // night — vanilla night window (>= 13000 of the day).
        define("night",
            (json, ctx) -> p -> p.level().getDayTime() % 24000L >= 13000L, List.of());
        // in_rain — raining at the player's exposed position (canSeeSky-gated).
        define("in_rain", (json, ctx) -> p -> {
            if (!(p.level() instanceof ServerLevel sl)) return false;
            if (p.isPassenger()) return false;
            BlockPos pos = p.blockPosition();
            return sl.isRainingAt(pos) && sl.canSeeSky(pos);
        }, List.of());
        // exposed_to_sky — open sky directly above the player.
        define("exposed_to_sky", (json, ctx) -> p -> {
            if (!(p.level() instanceof ServerLevel sl)) return false;
            return sl.canSeeSky(p.blockPosition());
        }, List.of());
        // thundering — thunderstorm with rain falling at the player's position.
        define("thundering", (json, ctx) -> p -> {
            if (!(p.level() instanceof ServerLevel sl)) return false;
            return sl.isThundering() && sl.isRainingAt(p.blockPosition());
        }, List.of());

        // ---- Numeric comparison conditions (delegate to ConditionParser helpers) ----
        // Each delegates to the lifted package-private parse* helper so behaviour is
        // byte-identical; the FieldSpec list is transcribed from the helper's reads
        // and the hand-written schema (D2). All share the comparison/compare_to shape.
        define("health",
            (json, ctx) -> ConditionParser.parseHealth(json),
            List.of(comparison(">=", "Comparison operator (default >=)."),
                    compareTo(FormFieldSpec.Kind.NUMBER, 0.0, "Health value threshold (default 0).")));
        define("food_level", List.of("food"),
            (json, ctx) -> ConditionParser.parseFoodLevel(json),
            List.of(comparison(">=", "Comparison operator (default >=)."),
                    compareTo(FormFieldSpec.Kind.NUMBER, 0.0, "Food-level threshold (default 0).")));
        define("saturation_level",
            (json, ctx) -> ConditionParser.parseSaturationLevel(json),
            List.of(comparison(">=", "Comparison operator (default >=)."),
                    compareTo(FormFieldSpec.Kind.NUMBER, 0.0, "Saturation threshold (default 0).")));
        define("relative_health",
            (json, ctx) -> ConditionParser.parseRelativeHealth(json),
            List.of(comparison(">=", "Comparison operator (default >=)."),
                    compareTo(FormFieldSpec.Kind.NUMBER, 0.0, "Health ratio threshold 0..1 (default 0).")));
        define("fall_distance",
            (json, ctx) -> ConditionParser.parseFallDistance(json),
            List.of(comparison(">=", "Comparison operator (default >=)."),
                    compareTo(FormFieldSpec.Kind.NUMBER, 0.0, "Fall-distance threshold (default 0).")));
        // light_level and brightness share parseLightLevel but are BOTH first-class
        // picker entries (both in KNOWN_TYPES), so each is its own canonical
        // descriptor sharing the factory — not an alias-set (an alias would drop a
        // counted picker type). Same rationale for xp_level / xp_levels below.
        java.util.List<FieldSpec> lightFields = List.of(
            comparison(">=", "Comparison operator (default >=)."),
            compareTo(FormFieldSpec.Kind.INTEGER, 0, "Light-level threshold 0..15 (default 0)."),
            new FieldSpec("light_type", FormFieldSpec.Kind.ENUM, false)
                .options("sky", "block", "any").def("any")
                .doc("Which light layer to sample (default any/max local brightness)."));
        define("light_level", (json, ctx) -> ConditionParser.parseLightLevel(json), lightFields);
        define("brightness", (json, ctx) -> ConditionParser.parseLightLevel(json), lightFields);
        define("temperature",
            (json, ctx) -> ConditionParser.parseTemperature(json),
            List.of(comparison(">=", "Comparison operator (default >=)."),
                    compareTo(FormFieldSpec.Kind.NUMBER, 0.0, "Biome base-temperature threshold (default 0).")));
        define("armor_value",
            (json, ctx) -> ConditionParser.parseArmorValue(json),
            List.of(comparison(">=", "Comparison operator (default >=)."),
                    compareTo(FormFieldSpec.Kind.NUMBER, 0.0, "Armor-value threshold (default 0).")));
        define("amount",
            (json, ctx) -> ConditionParser.parseAmount(json),
            List.of(comparison(">=", "Comparison operator (default >=)."),
                    compareTo(FormFieldSpec.Kind.NUMBER, 0.0, "Threshold (standalone: compared against health; default 0).")));
        define("height",
            (json, ctx) -> ConditionParser.parseHeight(json),
            List.of(comparison(">=", "Comparison operator (default >=)."),
                    compareTo(FormFieldSpec.Kind.NUMBER, 0.0, "World Y-position threshold (default 0).")));
        // xp_level / xp_levels — both in KNOWN_TYPES, so both canonical (see note above).
        java.util.List<FieldSpec> xpLevelFields = List.of(
            comparison(">=", "Comparison operator (default >=)."),
            compareTo(FormFieldSpec.Kind.INTEGER, 0, "Experience-level threshold (default 0)."));
        define("xp_level", (json, ctx) -> ConditionParser.parseXpLevel(json), xpLevelFields);
        define("xp_levels", (json, ctx) -> ConditionParser.parseXpLevel(json), xpLevelFields);
        define("xp_points",
            (json, ctx) -> ConditionParser.parseXpPoints(json),
            List.of(comparison(">=", "Comparison operator (default >=)."),
                    compareTo(FormFieldSpec.Kind.INTEGER, 0, "Total-experience threshold (default 0).")));
        define("fluid_height",
            (json, ctx) -> ConditionParser.parseFluidHeight(json),
            List.of(comparison(">=", "Comparison operator (default >=)."),
                    compareTo(FormFieldSpec.Kind.NUMBER, 0.0, "Fluid-height threshold (default 0)."),
                    new FieldSpec("fluid", FormFieldSpec.Kind.STRING, false)
                        .doc("Fluid id to measure (minecraft:water / minecraft:lava).")));

        // ---- Identifier / tag conditions (delegate to ConditionParser helpers) ----
        define("dimension",
            (json, ctx) -> ConditionParser.parseDimension(json),
            List.of(new FieldSpec("dimension", FormFieldSpec.Kind.STRING, false)
                .doc("Dimension id the player must be in (absent → always true).")));
        define("in_tag",
            (json, ctx) -> ConditionParser.parseInTag(json),
            List.of(new FieldSpec("tag", FormFieldSpec.Kind.STRING, false)
                .doc("Biome tag the player's biome must be in (absent → always true).")));
        define("submerged_in",
            (json, ctx) -> ConditionParser.parseSubmergedIn(json),
            List.of(new FieldSpec("fluid", FormFieldSpec.Kind.STRING, false)
                .doc("Fluid id whose submersion to test (minecraft:water / minecraft:lava).")));
        define("entity_type",
            (json, ctx) -> ConditionParser.parseEntityType(json),
            List.of(new FieldSpec("entity_type", FormFieldSpec.Kind.STRING, false)
                .doc("Entity-type id to match (player is always minecraft:player; absent → always true).")));
        define("enchantment",
            (json, ctx) -> ConditionParser.parseEnchantment(json),
            List.of(new FieldSpec("enchantment", FormFieldSpec.Kind.STRING, false)
                        .doc("Enchantment id to look for across equipped items (absent → always true)."),
                    comparison(">=", "Comparison operator (default >=)."),
                    compareTo(FormFieldSpec.Kind.INTEGER, 1, "Enchantment-level threshold (default 1).")));
    }

    /** Descriptor for the given canonical {@code "neoorigins:<verb>"} id, or {@code null}. */
    public static ConditionType get(String canonicalType) {
        return BY_KEY.get(canonicalType);
    }

    /** All built-in condition descriptors, in registration order. */
    public static Map<ResourceLocation, ConditionType> descriptors() {
        return Collections.unmodifiableMap(DESCRIPTORS);
    }

    /**
     * Canonical {@code neoorigins:<verb>} id strings for every descriptor — the
     * type total the audit counts (aliases excluded, since an alias is not a
     * separate type).
     */
    public static java.util.Set<String> canonicalIds() {
        java.util.Set<String> ids = new java.util.TreeSet<>();
        for (ResourceLocation rl : DESCRIPTORS.keySet()) ids.add(rl.toString());
        return ids;
    }

    /**
     * Alias id strings across all descriptors (synonyms that dispatch to a
     * canonical verb). Surfaced so {@code SchemaFormCheck} can treat them as known
     * verbs in the {@code KNOWN_TYPES} parity check without counting them as
     * separate types.
     */
    public static java.util.Set<String> aliasIds() {
        java.util.Set<String> ids = new java.util.TreeSet<>();
        for (ConditionType t : DESCRIPTORS.values()) {
            for (ResourceLocation alias : t.aliases()) ids.add(alias.toString());
        }
        return ids;
    }
}
