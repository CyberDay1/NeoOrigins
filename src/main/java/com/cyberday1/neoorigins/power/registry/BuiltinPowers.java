package com.cyberday1.neoorigins.power.registry;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.power.builtin.*;
import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec.Kind;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static, class-load-time field-spec table for built-in power types — the power
 * analogue of {@link com.cyberday1.neoorigins.compat.action.BuiltinActions}, and
 * the second half of the registry refactor (powers, after the action/condition
 * verbs).
 *
 * <p><b>What this is NOT.</b> Powers already deserialize through their own
 * {@code Codec<Config>} via {@code PowerDataManager}; this table does
 * <em>not</em> touch that parse path, any power {@code Codec}, or any
 * {@code Config} record. It is <b>metadata only</b>: the per-power
 * {@link FieldSpec} list that drives the JSON schema, the doc reference tables,
 * and both editors' forms — data that historically lived in four hand-maintained
 * side-tables ({@code power.schema.json}, {@code field_docs.json},
 * {@code EnumHints}, {@code NeoOriginsConfig} ranges) that silently drift.
 * Consolidating it here, beside a drift audit, is the permanent fix.
 *
 * <p><b>Why static, not the live registry?</b> Same reason as
 * {@code BuiltinActions}: {@link PowerTypes#get} reads the
 * {@code neoorigins:power_type} registry, which only populates after
 * {@code NewRegistryEvent} fires — i.e. never in the headless harnesses
 * ({@code compatTest}, {@code goldenMaster}, {@code schemaFormCheck}). This
 * table is available the moment the class loads, with or without a running
 * NeoForge, so it can back {@link com.cyberday1.neoorigins.power.schemaform.FormModel}
 * and the {@code auditPowerFieldSpecs} drift guard headlessly. Each descriptor
 * also carries its power's {@code Class<?>} so the audit can resolve the
 * {@code Config} record via {@link com.cyberday1.neoorigins.power.schemaform.PowerConfigClassResolver}
 * without the live registry.
 *
 * <p>Migration is power-by-power (mirrors the verb migration): each entry added
 * here lets {@code FormModel.forPower} prefer the declared spec over the
 * schema-branch / codec-reflection fallback, gated on the golden master and
 * {@code SchemaFormCheck} staying green.
 */
public final class BuiltinPowers {

    private BuiltinPowers() {}

    /**
     * One built-in power's declarative metadata.
     *
     * @param id         canonical {@code neoorigins:<type>} id (registry key).
     * @param powerClass the concrete {@code PowerType<C>} class — carried so the
     *                   drift audit can resolve the {@code Config} record headlessly
     *                   (the live registry is empty in the harnesses).
     * @param fields     declared config fields, in author-facing order. Empty for
     *                   marker-only powers (no config to author).
     */
    public record PowerSpec(ResourceLocation id, Class<?> powerClass, List<FieldSpec> fields) {
        public PowerSpec {
            fields = List.copyOf(fields);
        }
    }

    /** Insertion-ordered so registration/audit output is deterministic. */
    private static final Map<ResourceLocation, PowerSpec> DESCRIPTORS = new LinkedHashMap<>();
    /** Canonical {@code "neoorigins:<type>"} string → descriptor, for fast lookup. */
    private static final Map<String, PowerSpec> BY_KEY = new java.util.HashMap<>();

    private static void define(String path, Class<?> powerClass, List<FieldSpec> fields) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, path);
        PowerSpec spec = new PowerSpec(id, powerClass, fields);
        DESCRIPTORS.put(id, spec);
        BY_KEY.put(id.toString(), spec);
    }

    static {
        // ── Group M — marker-only powers ────────────────────────────────────
        // No config fields: the empty form is correct (nothing to author). These
        // are the lowest-risk entries — registering them only makes the drift
        // audit count them; FormModel still resolves an empty field list either
        // way (declared-empty here, or codec-reflection-empty before), so this is
        // behavior-neutral. The marker set is exactly SchemaFormCheck
        // .auditPowerFormCoverage's "marker-only" list; each Config is
        // `record Config(String type)` (type is internal plumbing, not a field).
        define("cobweb_affinity",          CobwebAffinityPower.class,         List.of());
        define("ender_gaze_immunity",      EnderGazeImmunityPower.class,      List.of());
        define("flight",                   FlightPower.class,                 List.of());
        define("ignore_water",             IgnoreWaterPower.class,            List.of());
        define("natural_glide",            NaturalGlidePower.class,           List.of());
        define("no_natural_regen",         NoNaturalRegenPower.class,         List.of());
        define("no_projectile_divergence", NoProjectileDivergencePower.class, List.of());
        define("underwater_mining_speed",  UnderwaterMiningSpeedPower.class,  List.of());
        define("wall_climbing",            WallClimbingPower.class,           List.of());
        define("water_breathing",          WaterBreathingPower.class,         List.of());

        // ── Group R — record-reflectable single-knob powers ─────────────────
        // Plain-Config powers whose one user-facing field reflection already
        // sees by name+kind, but whose codec `optionalFieldOf` default and field
        // doc previously lived in the side-tables (field_docs.json). Declaring
        // the FieldSpec here carries name + optionality + default + doc in one
        // place; required=false mirrors the codec's optionalFieldOf, and the
        // collapsed field_docs.json entry is now sourced from the spec (see
        // SchemaFormCheck.auditFieldDocs / FormModel.enrich). Behavior-neutral:
        // the power still deserializes through its own Codec<Config>, untouched.
        define("break_speed_modifier", BreakSpeedModifierPower.class, List.of(
            new FieldSpec("multiplier", Kind.NUMBER, false)
                .def(2.0).doc("Mining-speed multiplier; stacks multiplicatively with other instances; default 2.0.")));
        define("crop_harvest_bonus", CropHarvestBonusPower.class, List.of(
            new FieldSpec("extra_drops", Kind.INTEGER, false)
                .def(1).doc("Extra copies of the block's own loot when breaking crops/logs (default 1).")));
        define("dodge_chance", DodgeChancePower.class, List.of(
            new FieldSpec("chance", Kind.NUMBER, false)
                .def(0.15).doc("Probability 0.0-1.0 to fully cancel an incoming damage event (default 0.15).")));
        define("entity_group", EntityGroupPower.class, List.of(
            new FieldSpec("group", Kind.STRING, false)
                .def("undefined").doc("Mob classification: undead, arthropod, water, or undefined (changes effect/enchant interactions).")));
        define("hide_hud_bar", HideHudBarPower.class, List.of(
            new FieldSpec("bar", Kind.STRING, false)
                .def("hunger").doc("Which HUD bar to hide: hunger/food or air/oxygen/breath (default hunger).")));
        define("item_magnetism", ItemMagnetismPower.class, List.of(
            new FieldSpec("radius", Kind.NUMBER, false)
                .def(4.0).doc("Blocks around the player within which dropped items are pulled in (default 4.0).")));
        define("lava_vision", LavaVisionPower.class, List.of(
            new FieldSpec("strength", Kind.NUMBER, false)
                .def(3.0).doc("Lava fog distance multiplier; higher sees farther in lava (default 3.0).")));
        define("more_smoker_xp", MoreSmokerXpPower.class, List.of(
            new FieldSpec("multiplier", Kind.NUMBER, false)
                .def(2.0).doc("Multiplier for smoker-cooking XP (default 2.0).")));
        define("sneaky", SneakyPower.class, List.of(
            new FieldSpec("detection_multiplier", Kind.NUMBER, false)
                .def(0.3).doc("Mob detection range multiplier; lower = sneakier (default 0.3).")));
        define("tamed_potion_diffusal", TamedPotionDiffusalPower.class, List.of(
            new FieldSpec("radius", Kind.NUMBER, false)
                .def(16.0).doc("Block radius for sharing positive potion effects with tamed animals.")));
        define("tree_felling", TreeFellingPower.class, List.of(
            new FieldSpec("max_blocks", Kind.INTEGER, false)
                .def(64).doc("Maximum connected log blocks broken in one chop (default 64).")));
        define("twin_breeding", TwinBreedingPower.class, List.of(
            new FieldSpec("chance", Kind.NUMBER, false)
                .def(1.0).doc("Probability 0.0-1.0 of an extra baby when animals breed (default 1.0).")));
    }

    /** Descriptor for the given canonical {@code "neoorigins:<type>"} id, or {@code null}. */
    public static PowerSpec get(String canonicalType) {
        return BY_KEY.get(canonicalType);
    }

    /** Descriptor for the given power id, or {@code null} if not registered here. */
    public static PowerSpec get(ResourceLocation id) {
        return DESCRIPTORS.get(id);
    }

    /** True when a field-spec descriptor exists for {@code id}. */
    public static boolean isRegistered(ResourceLocation id) {
        return DESCRIPTORS.containsKey(id);
    }

    /**
     * Declared {@link FieldSpec}s for {@code id}, or {@code null} when no
     * descriptor is registered. A registered marker-only power returns an
     * empty list (distinct from {@code null} = "not declared here, fall back").
     */
    public static List<FieldSpec> fieldsFor(ResourceLocation id) {
        PowerSpec s = DESCRIPTORS.get(id);
        return s == null ? null : s.fields();
    }

    /** {@link FieldSpec}s for {@code id}, or {@code null} — string-keyed overload. */
    public static List<FieldSpec> fieldsFor(String canonicalType) {
        PowerSpec s = BY_KEY.get(canonicalType);
        return s == null ? null : s.fields();
    }

    /** All built-in power descriptors, in registration order. */
    public static Map<ResourceLocation, PowerSpec> descriptors() {
        return Collections.unmodifiableMap(DESCRIPTORS);
    }

    /** Canonical {@code neoorigins:<type>} id strings for every descriptor. */
    public static java.util.Set<String> ids() {
        java.util.Set<String> ids = new java.util.TreeSet<>();
        for (ResourceLocation rl : DESCRIPTORS.keySet()) ids.add(rl.toString());
        return ids;
    }
}
