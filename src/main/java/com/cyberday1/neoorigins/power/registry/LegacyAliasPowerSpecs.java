package com.cyberday1.neoorigins.power.registry;

import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec.Kind;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Field-spec table for the 2.0 legacy power-type ids that {@link
 * LegacyPowerTypeAliases} remaps at load — the authorable surface {@link
 * BuiltinPowers} cannot describe.
 *
 * <p><b>Why this is separate from {@link BuiltinPowers}.</b> These ids are not
 * power types. They have no {@code PowerType} class, no {@code Config} record
 * and no {@code PowerTypes} registration: the alias table rewrites the JSON's
 * {@code type} to a modern type before {@code PowerTypes.get} is ever called.
 * Putting them in {@code BuiltinPowers.DESCRIPTORS} would therefore trip both
 * gates that guard that table — {@code PowerEnumCheck} ("descriptor with no
 * registration") and {@code SchemaFormCheck.auditPowerFieldSpecs} ("no Config
 * record resolved"). Both would be right to fail. So the descriptors live here,
 * are consumed only by {@code PowerSchemaGenerator}, and leave the built-in
 * registry's meaning intact.
 *
 * <p><b>Why it exists at all.</b> Without a {@code oneOf} branch an id falls to
 * {@code power.schema.json}'s permissive {@code type.not.enum} fallback, which
 * declares no properties: every editor renders a raw-JSON text box and validates
 * nothing. Every one of these ids loads perfectly well, so the effect is that a
 * type the mod's own content uses is unauthorable. These branches close that.
 *
 * <p><b>What a branch may declare.</b> One rule, applied per alias: <em>declare
 * exactly the fields that still mean something after the remap runs.</em> The
 * remap lambda is the arbiter, and it splits the aliases three ways:
 *
 * <ul>
 *   <li><b>Passthrough</b> (2-arg {@code register}, no lambda): the JSON is
 *       handed to the target verbatim, so the branch is the target's own field
 *       set, pulled live off {@link BuiltinPowers} by {@link #fieldsOf} rather
 *       than re-typed here — a copy would drift the first time the target gains
 *       a field.</li>
 *   <li><b>Passthrough with a forced value</b>: as above, minus the field the
 *       lambda overwrites (and minus anything that field renders inert).</li>
 *   <li><b>Translator</b> (3-arg): the lambda synthesises the target's
 *       fields from its own, so the branch declares the legacy fields the lambda
 *       READS and must not offer the target's — writing {@code entity_action} on
 *       a {@code damage_in_water} is a silent no-op, the lambda overwrites it.</li>
 * </ul>
 *
 * <p>The rule cuts the other way too, which is why the four {@code active_ability}
 * aliases carry a tail of target fields: {@code ActiveAbilityPower} reads
 * {@code cooldown_ticks}, {@code resource_cost}, {@code cooldown_icon} and the
 * rest straight off the same JSON, the lambda never touches them, and the mod's
 * own {@code gravity_mage_repulse} authors four of them. Omitting them would
 * hand the editors a form that silently drops working configuration.
 */
public final class LegacyAliasPowerSpecs {

    private LegacyAliasPowerSpecs() {}

    private static final Map<Identifier, List<FieldSpec>> SPECS = new LinkedHashMap<>();

    private static void define(String path, List<FieldSpec> fields) {
        Identifier id = Identifier.fromNamespaceAndPath("neoorigins", path);
        if (BuiltinPowers.isRegistered(id)) {
            throw new IllegalStateException(id + " is a real power type — describe it in "
                + "BuiltinPowers, not in the legacy-alias table");
        }
        SPECS.put(id, List.copyOf(fields));
    }

    /**
     * The remap target's own declared fields, minus {@code exclude}. Read live
     * from {@link BuiltinPowers} so a passthrough branch tracks its target.
     */
    private static List<FieldSpec> fieldsOf(String target, String... exclude) {
        List<FieldSpec> declared = BuiltinPowers.fieldsFor("neoorigins:" + target);
        if (declared == null) {
            throw new IllegalStateException("legacy alias target neoorigins:" + target
                + " has no BuiltinPowers descriptor to inherit fields from");
        }
        List<String> drop = List.of(exclude);
        List<FieldSpec> kept = new ArrayList<>();
        for (FieldSpec fs : declared) {
            if (!drop.contains(fs.name())) kept.add(fs);
        }
        if (kept.size() + drop.size() != declared.size()) {
            throw new IllegalStateException("legacy alias target neoorigins:" + target
                + " no longer declares all of " + drop + " — the exclusion is stale");
        }
        return kept;
    }

    /** {@code own} fields first (author-facing), then the inherited tail. */
    private static List<FieldSpec> concat(List<FieldSpec> own, List<FieldSpec> tail) {
        List<FieldSpec> all = new ArrayList<>(own);
        all.addAll(tail);
        return all;
    }

    /**
     * The namespaces one legacy Origins power id is authorable under.
     * {@code OriginsFormatDetector.legacyPowerTypeSurface()} expands every
     * {@code origins:} entry in the compat dispatch tables across the
     * Apoli family, and {@code canonicalizePowerType} rewrites them all back to
     * {@code origins:} before dispatch — so all four spellings load, all four
     * reach the enum, and all four need the same branch.
     */
    private static final List<String> LEGACY_COMPAT_NAMESPACES =
        List.of("origins", "apace", "apoli", "apugli");

    /**
     * Declare one branch for a legacy <em>compat</em> power type — an
     * {@code origins:}-family id served by {@code OriginsCompatPowerLoader}
     * (Route B) rather than by the 2.0 alias table.
     *
     * <p>Same contract as {@link #define}: declare exactly the fields the
     * parser reads. The difference is only in which parser is the arbiter —
     * here it is the Route B {@code parse*} method for the type, not a remap
     * lambda. Registering the branch keeps the id out of
     * {@code PowerEnumCheck}'s unbranched-legacy count, which may only shrink;
     * a new compat type that skipped this would push that ratchet up by four.
     */
    private static void defineLegacyCompat(String path, List<FieldSpec> fields) {
        List<FieldSpec> copy = List.copyOf(fields);
        for (String ns : LEGACY_COMPAT_NAMESPACES) {
            Identifier id = Identifier.fromNamespaceAndPath(ns, path);
            if (BuiltinPowers.isRegistered(id)) {
                throw new IllegalStateException(id + " is a real power type — describe it in "
                    + "BuiltinPowers, not in the legacy-alias table");
            }
            SPECS.put(id, copy);
        }
    }

    static {
        registerPersistentEffectAliases();
        registerConditionPassiveAliases();
        registerActionOnEventAliases();
        registerActiveAbilityAliases();
        registerLegacyCompatSpecs();
    }

    // ── persistent_effect targets ───────────────────────────────────────────

    private static void registerPersistentEffectAliases() {
        // status_effect — PASSTHROUGH. persistent_effect's own fields, plus the
        // root-level single-effect shorthand its hand-rolled codec reads but
        // BuiltinPowers cannot declare (those keys fold INTO each EffectSpec and
        // are not Config record components, which the drift audit requires). The
        // shorthand IS the legacy status_effect shape — example-pack's
        // status_effect power authors effect/amplifier/ambient/show_particles —
        // so leaving it out would be the whole reason this branch exists.
        define("status_effect", concat(List.of(
            new FieldSpec("effect", Kind.STRING, false)
                .doc("Mob-effect id applied while the power is active (e.g. 'minecraft:speed'). The legacy single-effect shorthand: authoring it here is equivalent to a one-entry `effects` list. Applied at infinite duration, so no duration field."),
            new FieldSpec("amplifier", Kind.INTEGER, false)
                .def(0).range(0.0, null)
                .doc("Effect level minus one for the root-level `effect` (0 = level I, 1 = level II); default 0. Also cascades as the default amplifier onto every `effects` entry that omits its own."),
            new FieldSpec("ambient", Kind.BOOLEAN, false)
                .def(false)
                .doc("When true the effect renders as ambient (faint beacon-style particles); default false. Cascades onto `effects` entries that omit it."),
            new FieldSpec("show_particles", Kind.BOOLEAN, false)
                .def(true)
                .doc("When false the effect's swirling particles are suppressed; default true. Cascades onto `effects` entries that omit it."),
            new FieldSpec("show_icon", Kind.BOOLEAN, false)
                .def(true)
                .doc("When false the effect's HUD status icon is hidden; default true. Cascades onto `effects` entries that omit it.")),
            fieldsOf("persistent_effect")));

        // stacking_status_effects — PASSTHROUGH with `toggleable` forced false.
        // Dropping toggleable also drops the three fields it gates:
        // PersistentEffectPower only honours default_off when toggleable (see its
        // Config decode), and cooldown_icon / always_show_icon place the power in
        // the ability HUD cluster, which non-toggleable powers never join.
        define("stacking_status_effects", fieldsOf("persistent_effect",
            "toggleable", "default_off", "cooldown_icon", "always_show_icon"));

        // night_vision — TRANSLATOR that reads NOTHING. writeSingleEffect replaces
        // `effects` wholesale with minecraft:night_vision, and toggleable is forced
        // false, which also inerts default_off / cooldown_icon / always_show_icon.
        //
        // toggleable:false is load bearing, not incidental: a toggleable
        // persistent_effect counts as an active power, claims one of the six
        // ability slots and answers to the skill keys — which is exactly how night
        // vision used to get switched off by a stray keypress and get reported as
        // broken. Players toggle it with the dedicated "Toggle Night Vision"
        // keybind, off the slot system. Do not add `toggleable` here to "let
        // authors add a toggle"; the toggle already exists.
        //
        // `condition` and `enabled` do survive the remap, but are left off for the
        // same reason: this alias exists to be a zero-config marker, and the modern
        // spelling (a persistent_effect with an explicit effects list) is the place
        // to gate or disable one. All 21 uses in the mod's own content are the bare
        // {"type": "neoorigins:night_vision"}.
        define("night_vision", List.of());

        // glow — TRANSLATOR that reads NOTHING: writeSingleEffect replaces
        // `effects` with minecraft:glowing. Unlike night_vision it leaves
        // toggleable alone, so the power keeps persistent_effect's toggle
        // default (true) — but there is nothing on the alias itself to author.
        define("glow", List.of());
    }

    // ── condition_passive targets ───────────────────────────────────────────
    //
    // Every translator here writes condition + entity_action + interval, so none
    // of the three may be declared: they are overwritten before the codec runs.

    private static void registerConditionPassiveAliases() {
        // action_over_time — PASSTHROUGH. A structural twin of condition_passive
        // (same interval / condition / entity_action fields) that was documented
        // in POWER_TYPES.md but never registered as a real type.
        define("action_over_time", fieldsOf("condition_passive"));

        define("biome_buff", List.of(
            new FieldSpec("biome_tag", Kind.STRING, false)
                .def("")
                .doc("Biome tag the player must be standing in for the effect to apply (e.g. '#minecraft:is_forest')."),
            new FieldSpec("effect", Kind.STRING, false)
                .def("minecraft:regeneration")
                .doc("Mob-effect id applied while in the biome; default minecraft:regeneration. Reapplied every second for 15s, so it persists while the condition holds and fades shortly after leaving."),
            new FieldSpec("amplifier", Kind.INTEGER, false)
                .def(0).range(0.0, null)
                .doc("Effect level minus one (0 = level I, 1 = level II); default 0.")));

        define("damage_in_biome", List.of(
            new FieldSpec("biome_tag", Kind.STRING, false)
                .def("")
                .doc("Biome tag the damage applies in (e.g. '#minecraft:is_badlands'). Ignored when `biomes` is present."),
            new FieldSpec("biomes", Kind.ARRAY, false)
                .doc("Explicit biome ids the damage applies in, OR-matched. Use instead of `biome_tag` for a set of biomes that share no vanilla tag (e.g. the desert + badlands variants). Takes precedence over `biome_tag`."),
            new FieldSpec("damage_per_second", Kind.NUMBER, false)
                .def(1.0)
                .doc("Damage dealt once per second while in a matching biome; default 1.0."),
            new FieldSpec("damage_type", Kind.STRING, false)
                .def("generic")
                .doc("Damage-source name used for the hurt (e.g. 'generic', 'on_fire', 'dry_out'); default generic.")));

        define("damage_in_daylight", List.of(
            new FieldSpec("damage_per_second", Kind.NUMBER, false)
                .def(1.0)
                .doc("Damage dealt once per second while exposed to sunlight and not in water; default 1.0. Set to 0 to burn without direct damage."),
            new FieldSpec("ignite", Kind.BOOLEAN, false)
                .def(false)
                .doc("When true the player is also set on fire each second in the sun; default false. Combines with damage_per_second — the vanilla fire ticks deal their own damage on top."),
            new FieldSpec("fire_ticks", Kind.INTEGER, false)
                .def(40).range(0.0, null)
                .doc("Burn duration in ticks applied per second when ignite is true (20 = 1s); default 40.")));

        // The `multiplier` scalar is real, not a spelling of damage_per_second: the
        // remap computes damage_per_second * multiplier. Several origins register
        // only `multiplier` as their power_overrides config key, which is how a
        // server admin retunes (or, at 0, disables) the weakness without editing
        // the power.
        define("damage_in_water", List.of(
            new FieldSpec("damage_per_second", Kind.NUMBER, false)
                .def(1.0)
                .doc("Base damage dealt once per second while in water (or rain); default 1.0."),
            new FieldSpec("multiplier", Kind.NUMBER, false)
                .def(1.0).range(0.0, null)
                .doc("Scalar applied to damage_per_second, so the damage per second is the product of the two; default 1.0. Exists so an origin can expose one power_overrides knob that retunes the weakness; 0 disables the damage entirely (the power still loads, but never calls hurt, so there is no hurt sound or animation)."),
            new FieldSpec("include_rain", Kind.BOOLEAN, false)
                .def(true)
                .doc("When true the damage also applies while exposed to rain, not just while in water; default true.")));

        define("burn_at_health_threshold", List.of(
            new FieldSpec("threshold_percent", Kind.NUMBER, false)
                .def(0.25).range(0.0, 1.0)
                .doc("Fraction of max health at or below which the player catches fire, as 0..1; default 0.25 (a quarter heart bar)."),
            new FieldSpec("fire_ticks", Kind.INTEGER, false)
                .def(60).range(0.0, null)
                .doc("Burn duration in ticks reapplied each second while below the threshold (20 = 1s); default 60.")));

        define("regen_in_fluid", List.of(
            new FieldSpec("fluid", Kind.ENUM, false)
                .options("water", "lava").def("water")
                .doc("Fluid the player must be in to heal: water (default) or lava."),
            new FieldSpec("amount_per_second", Kind.NUMBER, false)
                .def(1.0)
                .doc("Health points restored once per second while submerged; default 1.0 (half a heart).")));
    }

    // ── action_on_event targets ─────────────────────────────────────────────
    //
    // Every translator here writes `event`, and either `modifier` (the ten
    // Origins-Classes modifier hooks) or `entity_action` (the four action hooks),
    // so none of those may be declared. Neither may action_on_event's per-event
    // filters — block_condition, hands/hand, item_condition, effect_tag, power,
    // immunity_ticks — because the alias pins the event to one the filter does
    // not apply to, which makes writing one exactly the invisible no-op these
    // branches exist to prevent.

    private static void registerActionOnEventAliases() {
        define("hunger_drain_modifier", List.of(
            new FieldSpec("multiplier", Kind.NUMBER, false)
                .def(1.0).range(0.0, null)
                .doc("Scalar on exhaustion gain, so hunger drains at this rate: below 1 drains slower, above 1 faster; default 1.0 (vanilla).")));

        define("natural_regen_modifier", List.of(
            new FieldSpec("multiplier", Kind.NUMBER, false)
                .def(1.0).range(0.0, null)
                .doc("Scalar on natural health regeneration: below 1 heals slower, above 1 faster, 0 disables it; default 1.0 (vanilla).")));

        define("knockback_modifier", List.of(
            new FieldSpec("multiplier", Kind.NUMBER, false)
                .def(1.0).range(0.0, null)
                .doc("Scalar on knockback taken: below 1 for a heavier build, above 1 to be thrown further, 0 for immunity; default 1.0 (vanilla).")));

        define("longer_potions", List.of(
            new FieldSpec("duration_multiplier", Kind.NUMBER, false)
                .def(1.0).range(0.0, null)
                .doc("Scalar on the duration of potion effects the player drinks; default 1.0 (vanilla).")));

        define("more_animal_loot", List.of(
            new FieldSpec("multiplier", Kind.NUMBER, false)
                .def(1.0).range(0.0, null)
                .doc("Scalar on harvest drop counts from killed mobs; default 1.0 (vanilla).")));

        define("efficient_repairs", List.of(
            new FieldSpec("cost_multiplier", Kind.NUMBER, false)
                .def(1.0).range(0.0, null)
                .doc("Scalar on the XP level cost of anvil operations: below 1 makes repairs cheaper; default 1.0 (vanilla).")));

        define("better_enchanting", List.of(
            new FieldSpec("bonus_levels", Kind.INTEGER, false)
                .def(5)
                .doc("Enchanting power added to the enchanting table's level calculation, as if this many extra bookshelves were present; default 5. Additive, not a multiplier.")));

        define("better_crafted_food", List.of(
            new FieldSpec("saturation_bonus", Kind.NUMBER, false)
                .def(0.5)
                .doc("Saturation added to food this player crafts; default 0.5. Additive, not a multiplier.")));

        define("better_bone_meal", List.of(
            new FieldSpec("extra_applications", Kind.INTEGER, false)
                .def(1).range(0.0, null)
                .doc("Additional bone-meal growth applications per use; default 1 (so one use grows the crop twice).")));

        define("teleport_range_modifier", List.of(
            new FieldSpec("multiplier", Kind.NUMBER, false)
                .def(2.0).range(0.0, null)
                .doc("Scalar on ender-pearl / teleport range; default 2.0 (double vanilla).")));

        // `duration`, `effect` and `amplifier` are read only by the branch of the
        // switch their `action` names, but the lambda strips all three regardless,
        // so declaring them is honest for the whole type.
        define("action_on_hit_taken", List.of(
            new FieldSpec("action", Kind.ENUM, false)
                .options("teleport", "ignite_attacker", "effect_on_attacker").def("teleport")
                .doc("What happens when the player is hurt: teleport (random blink, 16 blocks horizontally / 8 vertically), ignite_attacker, or effect_on_attacker. Default teleport, which is also the fallback for an unrecognised value."),
            new FieldSpec("chance", Kind.NUMBER, false)
                .def(1.0).range(0.0, 1.0)
                .doc("Probability the reaction fires, as 0..1; default 1.0 (always)."),
            new FieldSpec("min_damage", Kind.NUMBER, false)
                .def(0.0).range(0.0, null)
                .doc("Minimum incoming damage before the reaction fires; default 0 (any hit). Rolled after `chance`."),
            new FieldSpec("duration", Kind.INTEGER, false)
                .range(0.0, null)
                .doc("ignite_attacker: burn duration in ticks (default 60). effect_on_attacker: effect duration in ticks (default 100). Ignored by teleport."),
            new FieldSpec("effect", Kind.STRING, false)
                .doc("effect_on_attacker only: the mob-effect id inflicted on whoever hit the player (e.g. 'minecraft:poison'). Ignored by the other actions."),
            new FieldSpec("amplifier", Kind.INTEGER, false)
                .def(0).range(0.0, null)
                .doc("effect_on_attacker only: effect level minus one (0 = level I); default 0. Ignored by the other actions.")));

        define("thorns_aura", List.of(
            new FieldSpec("return_ratio", Kind.NUMBER, false)
                .def(0.25).range(0.0, null)
                .doc("Fraction of the incoming damage reflected back at the attacker as magic damage; default 0.25.")));

        define("food_restriction", List.of(
            new FieldSpec("item_tag", Kind.MIXED, false)
                .mixedTypes("string", "array")
                .doc("The tag (or item id) the rule matches against, or an array of them, OR-matched. A bare value is read as a tag, so 'neoorigins:vampire_foods' and '#neoorigins:vampire_foods' are the same thing."),
            new FieldSpec("allowed_tags", Kind.MIXED, false)
                .mixedTypes("string", "array")
                .doc("Alias for `item_tag`, read only when `item_tag` is absent. Same single-or-array shape."),
            new FieldSpec("mode", Kind.ENUM, false)
                .options("blacklist", "whitelist").def("blacklist")
                .doc("blacklist (default) forbids eating anything the tags match; whitelist forbids eating anything they do not.")));

        define("action_on_kill", List.of(
            new FieldSpec("action", Kind.ENUM, false)
                .options("restore_health", "restore_hunger", "grant_effect").def("restore_health")
                .doc("Reward for killing an entity: restore_health, restore_hunger, or grant_effect. Default restore_health, which is also the fallback for an unrecognised value."),
            new FieldSpec("amount", Kind.NUMBER, false)
                .def(4.0)
                .doc("restore_health: health points healed (default 4 = two hearts). restore_hunger: food points restored, truncated to a whole number. Ignored by grant_effect."),
            new FieldSpec("effect", Kind.STRING, false)
                .doc("grant_effect only: the mob-effect id granted to the killer (e.g. 'minecraft:strength'). Ignored by the other actions."),
            new FieldSpec("duration", Kind.INTEGER, false)
                .def(200).range(0.0, null)
                .doc("grant_effect only: effect duration in ticks (20 = 1s); default 200. Ignored by the other actions."),
            new FieldSpec("amplifier", Kind.INTEGER, false)
                .def(0).range(0.0, null)
                .doc("grant_effect only: effect level minus one (0 = level I); default 0. Ignored by the other actions.")));
    }

    // ── active_ability targets ──────────────────────────────────────────────

    /**
     * {@code active_ability}'s own fields minus {@code entity_action}, which every
     * one of these lambdas overwrites. The rest — cooldown, costs, condition,
     * fail_action, the HUD icon trio, key — are read by {@code ActiveAbilityPower}
     * off the same JSON and are untouched by the remap, so they are as authorable
     * on the alias as on the modern type.
     */
    private static List<FieldSpec> activeAbilityTail() {
        return fieldsOf("active_ability", "entity_action");
    }

    private static void registerActiveAbilityAliases() {
        define("active_launch", concat(List.of(
            new FieldSpec("power", Kind.NUMBER, false)
                .def(1.5)
                .doc("Upward velocity applied on activation, in blocks per tick; default 1.5.")),
            activeAbilityTail()));

        define("repulse", concat(List.of(
            new FieldSpec("radius", Kind.NUMBER, false)
                .def(6.0).range(0.0, null)
                .doc("Radius in blocks of the outward shove; default 6."),
            new FieldSpec("strength", Kind.NUMBER, false)
                .def(1.0)
                .doc("How hard entities inside the radius are pushed away; default 1.0. Players are included.")),
            activeAbilityTail()));

        define("active_aoe_effect", concat(List.of(
            new FieldSpec("radius", Kind.NUMBER, false)
                .def(8.0).range(0.0, null)
                .doc("Radius in blocks of the effect burst; default 8."),
            new FieldSpec("effect", Kind.STRING, false)
                .def("minecraft:weakness")
                .doc("Mob-effect id applied to everyone in range; default minecraft:weakness."),
            new FieldSpec("duration", Kind.INTEGER, false)
                .def(200).range(0.0, null)
                .doc("Effect duration in ticks (20 = 1s); default 200. `duration_ticks` is accepted as a synonym and wins if both are present."),
            new FieldSpec("duration_ticks", Kind.INTEGER, false)
                .range(0.0, null)
                .doc("Synonym for `duration`, accepted because packs in this repo mix the two spellings. Takes precedence when both are set."),
            new FieldSpec("amplifier", Kind.INTEGER, false)
                .def(0).range(0.0, null)
                .doc("Effect level minus one (0 = level I); default 0."),
            new FieldSpec("include_source", Kind.BOOLEAN, false)
                .def(false)
                .doc("When true the caster is also affected; default false. Leaving it false is what stops an offensive burst (instant damage, wither) from killing its own caster.")),
            activeAbilityTail()));

        define("healing_mist", concat(List.of(
            new FieldSpec("heal_amount", Kind.NUMBER, false)
                .def(6.0)
                .doc("Health points restored to each player in range; default 6 (three hearts)."),
            new FieldSpec("radius", Kind.NUMBER, false)
                .def(8.0).range(0.0, null)
                .doc("Radius in blocks of the heal; default 8. Only players are healed, never mobs."),
            new FieldSpec("heal_self", Kind.BOOLEAN, false)
                .def(true)
                .doc("When true the caster is healed too; default true.")),
            activeAbilityTail()));
    }

    // ── legacy compat (Route B) types ───────────────────────────────────────

    /**
     * Branches for the Route B compat types whose parsers live in
     * {@code OriginsCompatPowerLoader}. Each field list mirrors exactly what
     * that type's {@code parse*} method reads off the JSON — no more, so the
     * editors cannot offer a key the loader ignores, and no less, so they
     * cannot drop working configuration.
     */
    private static void registerLegacyCompatSpecs() {
        // modify_healing / modify_status_effect_duration share a shape: a
        // modifier list plus an optional gate. Both are read by
        // parseModifierList (which accepts the singular OR plural key, and a
        // single object OR an array) and parseConditionField.
        List<FieldSpec> modifierSeamFields = List.of(
            new FieldSpec("modifier", Kind.MIXED, false)
                .mixedTypes("object", "array")
                .doc("Apoli modifier, or an array of them, applied in order. Each entry is "
                   + "{operation, value}; the attribute-style operations are the ones this "
                   + "legacy type uses — addition, multiply_base, multiply_total — where "
                   + "multiply_* sum into base + base*Σvalue (so 0.5 = 1.5x, -0.5 = 0.5x)."),
            new FieldSpec("modifiers", Kind.MIXED, false).boundTo("modifier")
                .mixedTypes("object", "array")
                .doc("Plural alias for `modifier`, read only when `modifier` is absent. Same shape."),
            new FieldSpec("condition", Kind.REF, false).ref("condition.schema.json")
                .doc("Optional gate on the holder; the scale only applies while it passes (default always)."));

        defineLegacyCompat("modify_healing", modifierSeamFields);
        defineLegacyCompat("modify_status_effect_duration", modifierSeamFields);

        defineLegacyCompat("action_on_death", List.of(
            new FieldSpec("entity_action", Kind.REF, false).ref("action.schema.json")
                .doc("EntityAction run on the dying holder."),
            new FieldSpec("bientity_action", Kind.REF, false).ref("action.schema.json")
                .doc("Bi-entity action run with actor = the dying holder and target = the killer. "
                   + "Skipped entirely when the death had no living attacker (fall, drowning, /kill)."),
            new FieldSpec("condition", Kind.REF, false).ref("condition.schema.json")
                .doc("Optional gate on the holder, tested at death time (default always).")));
    }

    /**
     * Every legacy alias id that has a declared field surface, in declaration
     * order. Consumed by {@code PowerSchemaGenerator} to emit one {@code oneOf}
     * branch each. An empty list is a marker-only branch: the id is valid and
     * carries no configuration of its own.
     */
    public static Map<Identifier, List<FieldSpec>> specs() {
        return Collections.unmodifiableMap(SPECS);
    }
}
