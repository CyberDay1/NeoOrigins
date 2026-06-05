package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.registry.ConditionType;
import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
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

    /** Resource-location-shaped scalar-list hint ({@code namespace:path}). */
    private static final String RESOURCE_LOCATION_PATTERN = "^[a-z0-9_.-]+:[a-z0-9_./\\-]+$";
    /** Looser hint allowing an optional {@code #} tag prefix on a resource location. */
    private static final String TAG_OR_ID_PATTERN = "^#?[a-z0-9_.-]+:[a-z0-9_./\\-]+$";

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
    private static final Map<Identifier, ConditionType> DESCRIPTORS = new LinkedHashMap<>();
    /** Canonical {@code "neoorigins:<verb>"} string → descriptor, for hot-path dispatch. */
    private static final Map<String, ConditionType> BY_KEY = new java.util.HashMap<>();

    private static void define(String path, ConditionType.Factory factory, List<FieldSpec> fields) {
        Identifier id = Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, path);
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
        Identifier id = Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, path);
        List<Identifier> aliases = aliasPaths.stream()
            .map(p -> Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, p))
            .toList();
        ConditionType type = new ConditionType(id, factory, fields, aliases);
        DESCRIPTORS.put(id, type);
        BY_KEY.put(id.toString(), type);
        for (Identifier alias : aliases) BY_KEY.put(alias.toString(), type);
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
            (json, ctx) -> p -> p.level().getDefaultClockTime() % 24000L < 13000L, List.of());
        // night — vanilla night window (>= 13000 of the day).
        define("night",
            (json, ctx) -> p -> p.level().getDefaultClockTime() % 24000L >= 13000L, List.of());
        // in_rain — raining at the player's exposed position (canSeeSky-gated).
        // Umbrella-aware: holding a Vampires Need Umbrellas umbrella shields the
        // player from rain, mirroring exposed_to_sun's sun shielding, so rain-damage
        // origins (Wet Fur, True Hydrophobia) stop hurting while carrying one.
        define("in_rain", (json, ctx) -> p -> {
            if (!(p.level() instanceof ServerLevel sl)) return false;
            if (p.isPassenger()) return false;
            if (ConditionParser.neoorigins$isHoldingUmbrella(p)) return false;
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
        // hardness — destroy-hardness of the block in context (raycast block_action
        // gate, or the looked-at block as fallback). Used by Mage spell_break etc.
        define("hardness",
            (json, ctx) -> ConditionParser.parseHardness(json),
            List.of(comparison(">=", "Comparison operator (default >=)."),
                    compareTo(FormFieldSpec.Kind.NUMBER, 0.0, "Block hardness threshold (default 0).")));
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
                    new FieldSpec("fluid", FormFieldSpec.Kind.ENUM, false)
                        .options("minecraft:water", "minecraft:lava")
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
                        .doc("Entity-type id to match (player is always minecraft:player; absent → always true)."),
                    new FieldSpec("type_id", FormFieldSpec.Kind.STRING, false)
                        .doc("Alias for entity_type (entity_type condition only).")));
        define("enchantment",
            (json, ctx) -> ConditionParser.parseEnchantment(json),
            List.of(new FieldSpec("enchantment", FormFieldSpec.Kind.STRING, false)
                        .doc("Enchantment id to look for across equipped items (absent → always true)."),
                    comparison(">=", "Comparison operator (default >=)."),
                    compareTo(FormFieldSpec.Kind.INTEGER, 1, "Enchantment-level threshold (default 1).")));

        // ---- Damage-source conditions (read DamageSource from HitTakenContext) ----
        define("from_fire", (json, ctx) -> ConditionParser.parseFromFire(), List.of());
        define("from_projectile", (json, ctx) -> ConditionParser.parseFromProjectile(), List.of());
        define("from_explosion", (json, ctx) -> ConditionParser.parseFromExplosion(), List.of());
        define("damage_type",
            (json, ctx) -> ConditionParser.parseDamageType(json),
            List.of(new FieldSpec("damage_type", FormFieldSpec.Kind.STRING, false)
                .doc("Damage-type id the incoming damage must match (absent → false).")));
        define("damage_tag",
            (json, ctx) -> ConditionParser.parseDamageTag(json),
            List.of(new FieldSpec("tag", FormFieldSpec.Kind.STRING, false)
                .doc("Damage-type tag the incoming damage must be in (absent → false).")));
        // damage_name / name — `name` is a synonym (not in KNOWN_TYPES → alias).
        define("damage_name", List.of("name"),
            (json, ctx) -> ConditionParser.parseDamageName(json),
            List.of(new FieldSpec("name", FormFieldSpec.Kind.STRING, false)
                .doc("Damage-source message id to match, case-insensitive (absent → false).")));

        // ---- Bientity conditions (read target from current dispatch context) ----
        define("distance",
            (json, ctx) -> ConditionParser.parseDistance(json),
            List.of(comparison("<=", "Comparison operator (default <=)."),
                    compareTo(FormFieldSpec.Kind.NUMBER, 0.0, "Distance threshold to the target (default 0).")));
        define("can_see", (json, ctx) -> ConditionParser.parseCanSee(), List.of());
        define("equal", (json, ctx) -> ConditionParser.parseEqual(), List.of());
        define("target_type",
            (json, ctx) -> ConditionParser.parseTargetType(json),
            List.of(new FieldSpec("entity_type", FormFieldSpec.Kind.STRING, false)
                        .doc("Target entity-type id, or #tag (absent → false)."),
                    new FieldSpec("type_id", FormFieldSpec.Kind.STRING, false)
                        .doc("Alias for entity_type (entity_type condition only).")));
        define("target_group",
            (json, ctx) -> ConditionParser.parseTargetGroup(json),
            List.of(new FieldSpec("group", FormFieldSpec.Kind.STRING, false)
                .doc("Vanilla mob category/group the target must be in (absent → false).")));
        define("in_set",
            (json, ctx) -> ConditionParser.parseInSet(json, ctx),
            List.of(new FieldSpec("set", FormFieldSpec.Kind.STRING, true)
                .doc("Entity-set key on the actor; true iff the target's UUID is in it.")));

        // ---- Meta / world conditions (delegate to ConditionParser helpers) ----
        define("time_of_day",
            (json, ctx) -> ConditionParser.parseTimeOfDay(json),
            List.of(comparison(">=", "Comparison operator (default >=)."),
                    compareTo(FormFieldSpec.Kind.INTEGER, 0, "Day-time tick threshold 0..23999 (default 0).")));
        define("weather",
            (json, ctx) -> ConditionParser.parseWeather(json),
            List.of(new FieldSpec("state", FormFieldSpec.Kind.ENUM, false)
                        .options("clear", "rain", "thunder").def("clear")
                        .doc("Weather state to match (also accepts the `value` field; default clear)."),
                    new FieldSpec("value", FormFieldSpec.Kind.ENUM, false)
                        .options("clear", "rain", "thunder")
                        .doc("Alias for state.")));
        define("moon_phase",
            (json, ctx) -> ConditionParser.parseMoonPhase(json),
            List.of(comparison("==", "Comparison operator (default ==)."),
                    compareTo(FormFieldSpec.Kind.INTEGER, 0, "Moon phase 0..7 (0 = full moon, 4 = new moon; default 0).")));
        define("nbt",
            (json, ctx) -> ConditionParser.parseNbt(json),
            List.of(new FieldSpec("nbt", FormFieldSpec.Kind.STRING, false)
                .doc("Key the player's persistent data must contain (absent → always true).")));
        define("scoreboard",
            (json, ctx) -> ConditionParser.parseScoreboard(json),
            List.of(new FieldSpec("objective", FormFieldSpec.Kind.STRING, false)
                        .doc("Scoreboard objective to read the player's score from (absent → false)."),
                    comparison(">=", "Comparison operator (default >=)."),
                    compareTo(FormFieldSpec.Kind.INTEGER, 0, "Score threshold (default 0).")));
        define("command",
            (json, ctx) -> ConditionParser.parseCommand(json),
            List.of(new FieldSpec("command", FormFieldSpec.Kind.STRING, false)
                        .doc("Command to run; compared against its return value (blank → false)."),
                    comparison(">=", "Comparison operator (default >=)."),
                    compareTo(FormFieldSpec.Kind.INTEGER, 1, "Threshold for the command's return value (default 1).")));
        define("air",
            (json, ctx) -> ConditionParser.parseAir(json),
            List.of(comparison(">=", "Comparison operator (default >=)."),
                    compareTo(FormFieldSpec.Kind.NUMBER, 0.0, "Air-supply threshold 0..300 (default 0).")));
        define("status_effect",
            (json, ctx) -> ConditionParser.parseStatusEffect(json),
            List.of(new FieldSpec("effect", FormFieldSpec.Kind.STRING, false)
                        .doc("Mob-effect id the player must have (absent → false)."),
                    new FieldSpec("amplifier", FormFieldSpec.Kind.INTEGER, false)
                        .doc("Exact amplifier to require (sets both min and max)."),
                    new FieldSpec("min_amplifier", FormFieldSpec.Kind.INTEGER, false)
                        .doc("Lowest acceptable amplifier (default -1 = any)."),
                    new FieldSpec("max_amplifier", FormFieldSpec.Kind.INTEGER, false)
                        .doc("Highest acceptable amplifier (default unbounded).")));
        define("has_effect",
            (json, ctx) -> ConditionParser.parseHasEffect(json),
            List.of(new FieldSpec("effect", FormFieldSpec.Kind.STRING, false)
                .doc("Mob-effect id the player must currently have (absent → false).")));
        // replacable / replaceable — only `replacable` is in KNOWN_TYPES; `replaceable`
        // is a spelling synonym (not a picker entry) → alias, not a counted type.
        define("replacable", List.of("replaceable"),
            (json, ctx) -> ConditionParser.parseReplaceable(json), List.of());
        define("power",
            (json, ctx) -> ConditionParser.parsePower(json, ctx),
            List.of(new FieldSpec("power", FormFieldSpec.Kind.STRING, true)
                .doc("Power id the player must have been granted.")));

        // ---- Power / resource / cooldown conditions (delegate to ConditionParser) ----
        // resource / resource_level — compares a power's stored resource value.
        // `resource_level` is a true synonym (absent from KNOWN_TYPES) → alias.
        define("resource", List.of("resource_level"),
            (json, ctx) -> ConditionParser.parseResource(json, ctx),
            List.of(new FieldSpec("resource", FormFieldSpec.Kind.STRING, true)
                        .doc("Resource power id whose stored value to read."),
                    comparison(">=", "Comparison operator (default >=)."),
                    compareTo(FormFieldSpec.Kind.INTEGER, 0, "Resource-value threshold (default 0).")));
        // cooldown — true when the named power is NOT on cooldown (absent power → always true).
        define("cooldown",
            (json, ctx) -> ConditionParser.parseCooldown(json),
            List.of(new FieldSpec("power", FormFieldSpec.Kind.STRING, false)
                .doc("Power id whose cooldown to test (absent → always true).")));
        // power_active — true when the named (or wildcard) toggle power is on.
        define("power_active",
            (json, ctx) -> ConditionParser.parsePowerActive(json, ctx),
            List.of(new FieldSpec("power", FormFieldSpec.Kind.STRING, true)
                .doc("Toggle power id to test; `*` wildcard suffix-matches toggle keys.")));
        // power_type — true when any granted power's type matches the given id.
        define("power_type",
            (json, ctx) -> ConditionParser.parsePowerType(json, ctx),
            List.of(new FieldSpec("power_type", FormFieldSpec.Kind.STRING, false)
                        .doc("Power-type id to match across granted powers (also accepts `id`; bare → origins: prefixed)."),
                    new FieldSpec("id", FormFieldSpec.Kind.STRING, false)
                        .doc("Alias for power_type.")));

        // ---- Block / position conditions (delegate to ConditionParser) ----
        // The nested block_condition / item_condition shapes are lifted as-is — the
        // descriptor factory carries the existing parse logic verbatim (no parser
        // normalization). FieldSpecs transcribe the hand-written schema branches.
        // on_block — block beneath the player matches the nested block_condition.
        define("on_block",
            (json, ctx) -> ConditionParser.parseOnBlock(json, ctx),
            List.of(new FieldSpec("block_condition", FormFieldSpec.Kind.REF, false)
                .ref("block_condition.schema.json")
                .doc("Nested block condition (block id / in_tag / and / or) tested against the block below; absent → on any ground.")));
        // block — block at the player's position matches an id or tag.
        define("block",
            (json, ctx) -> ConditionParser.parseBlockCondition(json, ctx),
            List.of(new FieldSpec("block_condition", FormFieldSpec.Kind.REF, false)
                        .ref("block_condition.schema.json")
                        .doc("Optional nested block condition wrapper; otherwise the fields are read off the root."),
                    new FieldSpec("block", FormFieldSpec.Kind.STRING, false)
                        .doc("Block id the block at the player's position must match (also accepts `id`)."),
                    new FieldSpec("tag", FormFieldSpec.Kind.STRING, false)
                        .doc("Block tag the block at the player's position must be in (absent block+tag → always true).")));
        // in_block / in_block_anywhere — block at the player's position matches an id.
        // `in_block_anywhere` is a true synonym (absent from KNOWN_TYPES) → alias.
        define("in_block", List.of("in_block_anywhere"),
            (json, ctx) -> ConditionParser.parseInBlock(json, ctx),
            List.of(new FieldSpec("block_condition", FormFieldSpec.Kind.REF, false)
                .ref("block_condition.schema.json")
                .doc("Nested block condition with `block`/`id`; absent → always true.")));
        // equipped_item — item in the given slot matches the nested item_condition.
        define("equipped_item",
            (json, ctx) -> ConditionParser.parseEquippedItem(json, ctx),
            List.of(new FieldSpec("equipment_slot", FormFieldSpec.Kind.ENUM, false)
                        .options("head", "chest", "legs", "feet", "mainhand", "offhand").def("mainhand")
                        .doc("Equipment slot to inspect (default mainhand)."),
                    new FieldSpec("item_condition", FormFieldSpec.Kind.REF, false)
                        .ref("item_condition.schema.json")
                        .doc("Nested item condition; absent → slot-presence check (any item present).")));
        // predicate — Apoli meta-wrapper compiling a vanilla MC predicate.
        define("predicate",
            (json, ctx) -> ConditionParser.parsePredicate(json, ctx),
            List.of(new FieldSpec("predicate_type", FormFieldSpec.Kind.ENUM, true)
                        .options("biome", "block_state", "entity_properties", "fluid_state", "item", "location", "damage")
                        .doc("Which vanilla predicate to compile (damage fails closed; needs hit-context)."),
                    new FieldSpec("predicate", FormFieldSpec.Kind.OBJECT, true)
                        .doc("The predicate JSON, parsed against the matching vanilla codec.")));

        // ---- Context-aware conditions (read the active ActionContextHolder) ----
        // These fail closed (false) outside their event context; delegated as-is.
        // food_item_in_tag — current FOOD_EATEN stack is in the given tag (or matches id).
        define("food_item_in_tag",
            (json, ctx) -> ConditionParser.parseFoodItemInTag(json),
            List.of(new FieldSpec("tag", FormFieldSpec.Kind.STRING, false)
                .doc("Item tag (#-prefixed) the eaten stack must be in, or a bare item id to match exactly (absent → false).")));
        // food_item_id — current FOOD_EATEN stack matches the given item id exactly.
        define("food_item_id",
            (json, ctx) -> ConditionParser.parseFoodItemId(json),
            List.of(new FieldSpec("id", FormFieldSpec.Kind.STRING, false)
                .doc("Item id the eaten stack must match exactly (absent → false).")));
        // hit_taken_amount — current HIT_TAKEN damage amount vs a threshold.
        define("hit_taken_amount",
            (json, ctx) -> ConditionParser.parseHitTakenAmount(json),
            List.of(comparison(">=", "Comparison operator (default >=)."),
                    compareTo(FormFieldSpec.Kind.NUMBER, 0.0, "Incoming-damage threshold (default 0).")));
        // no_minions_alive — player has no tracked minions of the given key.
        define("no_minions_alive",
            (json, ctx) -> ConditionParser.parseNoMinionsAlive(json),
            List.of(new FieldSpec("key", FormFieldSpec.Kind.STRING, false)
                .def("tamer:tamed")
                .doc("Minion-tracker key to count (default tamer:tamed).")));

        // ---- Proximity / combat / Origins++ conditions (delegate to ConditionParser) ----
        // near_block / block_in_radius — a matching block within radius (cubic scan).
        // `block_in_radius` is a true synonym (absent from KNOWN_TYPES) → alias.
        define("near_block", List.of("block_in_radius"),
            (json, ctx) -> ConditionParser.parseNearBlock(json, ctx),
            List.of(new FieldSpec("radius", FormFieldSpec.Kind.INTEGER, false).def(4).range(1.0, 8.0)
                        .doc("Scan radius in blocks (default 4, clamped 1..8)."),
                    new FieldSpec("block", FormFieldSpec.Kind.STRING, false)
                        .doc("Single block id to match."),
                    new FieldSpec("blocks", FormFieldSpec.Kind.ARRAY, false)
                        .itemPattern(RESOURCE_LOCATION_PATTERN)
                        .doc("List of block ids to match (any → true)."),
                    new FieldSpec("tag", FormFieldSpec.Kind.STRING, false)
                        .doc("Single block tag to match (#-prefix optional)."),
                    new FieldSpec("tags", FormFieldSpec.Kind.ARRAY, false)
                        .itemPattern(TAG_OR_ID_PATTERN)
                        .doc("List of block tags to match (any → true)."),
                    new FieldSpec("block_condition", FormFieldSpec.Kind.REF, false)
                        .ref("block_condition.schema.json")
                        .doc("Origins block_in_radius shape: nested in_tag/block condition. Requires at least one of block/blocks/tag/tags/block_condition.")));
        // near_entity — an entity of the given type/tag within distance (AABB scan).
        define("near_entity",
            (json, ctx) -> ConditionParser.parseNearEntity(json, ctx),
            List.of(new FieldSpec("entity_type", FormFieldSpec.Kind.STRING, true)
                        .doc("Entity-type id, or #tag, to look for nearby."),
                    new FieldSpec("distance", FormFieldSpec.Kind.NUMBER, false).def(8.0).range(1.0, 64.0)
                        .doc("Search distance in blocks (default 8, clamped 1..64).")));
        // out_of_combat — at least `ticks` elapsed since the player last took damage.
        define("out_of_combat",
            (json, ctx) -> ConditionParser.parseOutOfCombat(json),
            List.of(new FieldSpec("ticks", FormFieldSpec.Kind.INTEGER, false).def(100).range(0.0, null)
                .doc("Ticks since last damage required (default 100 = 5 s).")));
        // actor_condition — unwrap and evaluate the inner condition on the player.
        define("actor_condition",
            (json, ctx) -> ConditionParser.parseActorCondition(json, ctx),
            List.of(new FieldSpec("condition", FormFieldSpec.Kind.REF, true)
                .ref("#")
                .doc("Inner condition evaluated on the actor (the player).")));
        // advancement — player has completed the given advancement.
        define("advancement",
            (json, ctx) -> ConditionParser.parseAdvancement(json, ctx),
            List.of(new FieldSpec("advancement", FormFieldSpec.Kind.STRING, true)
                .doc("Advancement id the player must have completed.")));

        // ---- Config / environment conditions (delegate to ConditionParser) ----
        // config_flag — live value of a named config tunable (unknown key → true).
        define("config_flag",
            (json, ctx) -> ConditionParser.parseConfigFlag(json),
            List.of(new FieldSpec("key", FormFieldSpec.Kind.STRING, false)
                .doc("Config-flag key whose live boolean to return (unknown key → true, with a warning).")));
        // exposed_to_sun — player in open, unprotected daylight (umbrella/helmet aware).
        define("exposed_to_sun",
            (json, ctx) -> ConditionParser.parseExposedToSun(json),
            List.of());

        // biome — TRIPLE-SHAPE (lift-and-shift of parseBiome; NOT normalized — all
        // three shapes parse exactly as before). The factory delegates verbatim; the
        // FieldSpec list is the oneOf-style union of the shapes, mirroring the
        // apply_effect precedent:
        //   (1) `biome`     — exact biome id at the player's position.
        //   (2) `tag`/`biome_tag` — biome tag membership (`biome_tag` is a synonym
        //                     field read by frostborn/piglin/strider JSONs).
        //   (3) `condition` — nested sub-condition; the temperature case compares the
        //                     biome base temperature, other sub-types fail closed.
        // Precedence (id > tag > condition > always-true) is enforced inside the
        // parser, not the schema. None of the shapes hard-fails → all optional.
        define("biome",
            (json, ctx) -> ConditionParser.parseBiome(json),
            List.of(
                new FieldSpec("biome", FormFieldSpec.Kind.STRING, false)
                    .doc("Exact biome id at the player's position (shape 1; highest precedence)."),
                new FieldSpec("tag", FormFieldSpec.Kind.STRING, false)
                    .doc("Biome tag the player's biome must be in (shape 2)."),
                new FieldSpec("biome_tag", FormFieldSpec.Kind.STRING, false)
                    .doc("Synonym for `tag` used by several built-in JSONs (shape 2)."),
                new FieldSpec("condition", FormFieldSpec.Kind.OBJECT, false)
                    .doc("Nested biome sub-condition (shape 3); `temperature` compares biome base temperature, other sub-types fail closed. Absent all of biome/tag/biome_tag/condition → always true.")));

        define("and",
            (json, ctx) -> ConditionParser.parseAnd(json, ctx),
            List.of(new FieldSpec("conditions", FormFieldSpec.Kind.ARRAY, false)
                .itemsRef("#")
                .doc("List of sub-conditions; all must pass (absent/empty → true).")));
        define("or",
            (json, ctx) -> ConditionParser.parseOr(json, ctx),
            List.of(new FieldSpec("conditions", FormFieldSpec.Kind.ARRAY, false)
                .itemsRef("#")
                .doc("List of sub-conditions; at least one must pass (absent/empty → false).")));
        define("not",
            (json, ctx) -> ConditionParser.parseNot(json, ctx),
            List.of(new FieldSpec("condition", FormFieldSpec.Kind.REF, true)
                .ref("#")
                .doc("Nested condition whose result is negated.")));
    }

    /** Descriptor for the given canonical {@code "neoorigins:<verb>"} id, or {@code null}. */
    public static ConditionType get(String canonicalType) {
        return BY_KEY.get(canonicalType);
    }

    /** All built-in condition descriptors, in registration order. */
    public static Map<Identifier, ConditionType> descriptors() {
        return Collections.unmodifiableMap(DESCRIPTORS);
    }

    /**
     * Canonical {@code neoorigins:<verb>} id strings for every descriptor — the
     * type total the audit counts (aliases excluded, since an alias is not a
     * separate type).
     */
    public static java.util.Set<String> canonicalIds() {
        java.util.Set<String> ids = new java.util.TreeSet<>();
        for (Identifier rl : DESCRIPTORS.keySet()) ids.add(rl.toString());
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
            for (Identifier alias : t.aliases()) ids.add(alias.toString());
        }
        return ids;
    }
}
