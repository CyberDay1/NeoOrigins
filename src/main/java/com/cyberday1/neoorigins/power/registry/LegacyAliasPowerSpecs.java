package com.cyberday1.neoorigins.power.registry;

import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec.Kind;
import net.minecraft.resources.ResourceLocation;

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
 */
public final class LegacyAliasPowerSpecs {

    private LegacyAliasPowerSpecs() {}

    private static final Map<ResourceLocation, List<FieldSpec>> SPECS = new LinkedHashMap<>();

    private static void define(String path, List<FieldSpec> fields) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("neoorigins", path);
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

    static {
        registerPersistentEffectAliases();
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

    /**
     * Every legacy alias id that has a declared field surface, in declaration
     * order. Consumed by {@code PowerSchemaGenerator} to emit one {@code oneOf}
     * branch each. An empty list is a marker-only branch: the id is valid and
     * carries no configuration of its own.
     */
    public static Map<ResourceLocation, List<FieldSpec>> specs() {
        return Collections.unmodifiableMap(SPECS);
    }
}
