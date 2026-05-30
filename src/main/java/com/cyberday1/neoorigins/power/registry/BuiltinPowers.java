package com.cyberday1.neoorigins.power.registry;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.power.builtin.*;
import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec.Kind;
import net.minecraft.resources.Identifier;

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
    public record PowerSpec(Identifier id, Class<?> powerClass, List<FieldSpec> fields) {
        public PowerSpec {
            fields = List.copyOf(fields);
        }
    }

    /** Insertion-ordered so registration/audit output is deterministic. */
    private static final Map<Identifier, PowerSpec> DESCRIPTORS = new LinkedHashMap<>();
    /** Canonical {@code "neoorigins:<type>"} string → descriptor, for fast lookup. */
    private static final Map<String, PowerSpec> BY_KEY = new java.util.HashMap<>();

    private static void define(String path, Class<?> powerClass, List<FieldSpec> fields) {
        Identifier id = Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, path);
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

        // ── Group R (cont.) — multi-knob record-reflectable powers ──────────
        // Same contract as the single-knob block above, just with several
        // fields. Each FieldSpec mirrors the codec's `optionalFieldOf` default
        // (required=false) or its bare `fieldOf` (required=true); docs are the
        // collapsed field_docs.json entries. None of these had a structured
        // power.schema.json branch (their fields were reflection + field_docs
        // only), so registering them carries name+optionality+default+doc in one
        // place and the field_docs.json entry can be dropped. Behavior-neutral:
        // the power still deserializes through its own Codec<Config>, untouched.
        define("bare_hand_tool", BareHandToolPower.class, List.of(
            new FieldSpec("tool", Kind.STRING, false)
                .def("minecraft:stone_pickaxe").doc("Vanilla tool item id the empty hand emulates for tier/break speed; default stone_pickaxe.")));
        define("breath_out_of_fluid", BreathOutOfFluidPower.class, List.of(
            new FieldSpec("fluid", Kind.STRING, false)
                .def("water").doc("Fluid the player must stay in to breathe; drying on land drains air."),
            new FieldSpec("drain_rate", Kind.INTEGER, false)
                .def(40).doc("Ticks between each air drain while out of the fluid (20 = 1s).")));
        define("burn", BurnPower.class, List.of(
            new FieldSpec("interval", Kind.INTEGER, false)
                .def(20).doc("Ticks between each fire application; <=0 disables (default 20)."),
            new FieldSpec("burn_duration", Kind.INTEGER, false)
                .def(100).doc("Ticks of fire set on the player each application (20 = 1s; default 100).")));
        define("command_pack", CommandPackPower.class, List.of(
            new FieldSpec("range", Kind.NUMBER, false)
                .def(32.0).doc("Max block distance of the look-targeted entity your tamed mobs attack (default 32)."),
            new FieldSpec("cooldown_ticks", Kind.INTEGER, false)
                .def(40).doc("Ticks before this ability can be triggered again (default 40).")));
        define("craft_amount_bonus", CraftAmountBonusPower.class, List.of(
            new FieldSpec("output_item", Kind.STRING, false)
                .def("minecraft:oak_planks").doc("Item id whose crafting triggers the bonus (default oak_planks)."),
            new FieldSpec("bonus_count", Kind.INTEGER, false)
                .def(4).doc("Extra copies of the output added per craft (default 4; skipped if <=0).")));
        define("crop_growth_accelerator", CropGrowthAcceleratorPower.class, List.of(
            new FieldSpec("radius", Kind.INTEGER, false)
                .def(4).doc("Cubic block radius around the player scanned for crops (default 4)."),
            new FieldSpec("tick_interval", Kind.INTEGER, false)
                .def(40).doc("Ticks between growth passes; <=0 disables (default 40)."),
            new FieldSpec("growths_per_interval", Kind.INTEGER, false)
                .def(1).doc("Number of random nearby crops bone-mealed each interval (default 1).")));
        define("elytra_boost", ElytraBoostPower.class, List.of(
            new FieldSpec("strength", Kind.NUMBER, false)
                .def(1.5).doc("Multiplier on the forward impulse applied while elytra gliding (default 1.5)."),
            new FieldSpec("cooldown_ticks", Kind.INTEGER, false)
                .def(40).doc("Ticks before the boost can be triggered again (default 40).")));
        define("exhaustion_filter", ExhaustionFilterPower.class, List.of(
            new FieldSpec("sources", Kind.ARRAY, false)
                .doc("Exhaustion sources to suppress, e.g. sprint, mining (default [sprint]).")));
        define("extra_inventory", ExtraInventoryPower.class, List.of(
            new FieldSpec("size", Kind.INTEGER, false)
                .def(9).doc("Slot count, rounded to a multiple of 9 (min 9, max 54; default 9)."),
            new FieldSpec("drop_on_death", Kind.BOOLEAN, false)
                .def(false).doc("If true the extra inventory drops on death instead of persisting (default false).")));
        define("fortune_when_effect", FortuneWhenEffectPower.class, List.of(
            new FieldSpec("effect", Kind.STRING, false)
                .def("minecraft:luck").doc("Mob effect that must be active on the player for the Fortune bonus."),
            new FieldSpec("level", Kind.INTEGER, false)
                .def(2).doc("Virtual Fortune level rolled via the vanilla ore-drops formula."),
            new FieldSpec("target", Kind.STRING, false)
                .def("#c:ores").doc("Block tag the bonus applies to (default #c:ores; ancient debris excluded).")));
        define("horde_regen", HordeRegenPower.class, List.of(
            new FieldSpec("heal_amount", Kind.NUMBER, false)
                .def(1.0).doc("Health restored to each eligible tamed mob per interval (default 1.0)."),
            new FieldSpec("interval_ticks", Kind.INTEGER, false)
                .def(120).doc("Ticks between each horde-healing pass (default 120 = 6s)."),
            new FieldSpec("combat_cooldown_ticks", Kind.INTEGER, false)
                .def(100).doc("Ticks a tamed mob must avoid damage before it can heal (default 100).")));
        define("invulnerability", InvulnerabilityPower.class, List.of(
            new FieldSpec("damage_types", Kind.ARRAY, false)
                .doc("Damage-type ids (e.g. minecraft:fall) whose damage is cancelled."),
            new FieldSpec("damage_tags", Kind.ARRAY, false)
                .doc("Damage-type tag ids (e.g. minecraft:is_fire) whose damage is cancelled."),
            new FieldSpec("msg_ids", Kind.ARRAY, false)
                .doc("Vanilla damage msgId strings (e.g. inFire, fall) whose damage is cancelled.")));
    }

    /** Descriptor for the given canonical {@code "neoorigins:<type>"} id, or {@code null}. */
    public static PowerSpec get(String canonicalType) {
        return BY_KEY.get(canonicalType);
    }

    /** Descriptor for the given power id, or {@code null} if not registered here. */
    public static PowerSpec get(Identifier id) {
        return DESCRIPTORS.get(id);
    }

    /** True when a field-spec descriptor exists for {@code id}. */
    public static boolean isRegistered(Identifier id) {
        return DESCRIPTORS.containsKey(id);
    }

    /**
     * Declared {@link FieldSpec}s for {@code id}, or {@code null} when no
     * descriptor is registered. A registered marker-only power returns an
     * empty list (distinct from {@code null} = "not declared here, fall back").
     */
    public static List<FieldSpec> fieldsFor(Identifier id) {
        PowerSpec s = DESCRIPTORS.get(id);
        return s == null ? null : s.fields();
    }

    /** {@link FieldSpec}s for {@code id}, or {@code null} — string-keyed overload. */
    public static List<FieldSpec> fieldsFor(String canonicalType) {
        PowerSpec s = BY_KEY.get(canonicalType);
        return s == null ? null : s.fields();
    }

    /** All built-in power descriptors, in registration order. */
    public static Map<Identifier, PowerSpec> descriptors() {
        return Collections.unmodifiableMap(DESCRIPTORS);
    }

    /** Canonical {@code neoorigins:<type>} id strings for every descriptor. */
    public static java.util.Set<String> ids() {
        java.util.Set<String> ids = new java.util.TreeSet<>();
        for (Identifier rl : DESCRIPTORS.keySet()) ids.add(rl.toString());
        return ids;
    }
}
