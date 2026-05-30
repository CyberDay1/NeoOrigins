package com.cyberday1.neoorigins.dev;

import com.cyberday1.neoorigins.power.schemaform.CodecFieldSpecExtractor;
import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec;
import com.cyberday1.neoorigins.power.schemaform.FormModel;
import com.cyberday1.neoorigins.power.schemaform.PowerConfigClassResolver;
import com.cyberday1.neoorigins.power.schemaform.SchemaFormModel;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Headless packaging + parse guard for the 2.1 creator's field-spec layer.
 *
 * <p>Replaces the throwaway 2.1 spikes ({@code SchemaFormSpike} /
 * {@code CodecFieldSpike}) — those proved the approach; this proves the
 * <em>production</em> path stays wired: that {@code power.schema.json} is
 * actually on the runtime classpath (the {@code processResources} copy ran)
 * and parses into {@link SchemaFormModel}, and that
 * {@link CodecFieldSpecExtractor} still reflects a core power Config.
 *
 * <p>Invoke via {@code ./gradlew schemaFormCheck}. Exit 1 on any failure.
 */
public final class SchemaFormCheck {

    private SchemaFormCheck() {}

    public static void main(String[] args) {
        int failures = 0;

        // 1. Schema is packaged on the classpath and parses.
        SchemaFormModel model;
        try {
            model = SchemaFormModel.loadFromClasspath();
        } catch (RuntimeException e) {
            System.out.println("[schema-check] FAIL  classpath load: " + e.getMessage());
            System.exit(1);
            return;
        }

        // 2. The type universe is non-empty.
        int types = model.allTypes().size();
        if (types == 0) {
            System.out.println("[schema-check] FAIL  schema yielded 0 power types");
            failures++;
        }

        // 3. A known structured branch still resolves with fields. (Uses a power
        //    that is NOT yet registered in BuiltinPowers — registering a power
        //    collapses its schema branch, so the sample must be a still-branched
        //    type; attribute_modifier, mob_behavior and persistent_effect moved to
        //    the spec, so this points at particle, which keeps its schema branch.)
        String structuredSample = "neoorigins:particle";
        if (!model.hasStructuredForm(structuredSample)) {
            System.out.println("[schema-check] FAIL  expected structured branch for "
                + structuredSample);
            failures++;
        } else if (model.formFor(structuredSample).isEmpty()) {
            System.out.println("[schema-check] FAIL  " + structuredSample
                + " resolved to an empty form");
            failures++;
        }

        // 4. Codec-reflection fallback still extracts a core power Config.
        try {
            Class<?> cfg = Class.forName(
                "com.cyberday1.neoorigins.power.builtin.SizeScalingPower$Config");
            List<FormFieldSpec> f = CodecFieldSpecExtractor.extract(cfg);
            if (f.isEmpty()) {
                System.out.println("[schema-check] FAIL  SizeScalingPower$Config "
                    + "extracted 0 fields (expected scale/modify_reach)");
                failures++;
            }
        } catch (ReflectiveOperationException e) {
            System.out.println("[schema-check] FAIL  cannot load SizeScalingPower$Config: "
                + e);
            failures++;
        }

        // 5. PowerConfigClassResolver walks direct + intermediate-base chains.
        record ResolveCase(String powerClass, String expectConfig) {}
        for (ResolveCase rc : List.of(
                // direct: extends PowerType<C>
                new ResolveCase("com.cyberday1.neoorigins.power.builtin.SizeScalingPower",
                    "SizeScalingPower$Config"),
                // intermediate: extends AbstractTogglePower<C>
                new ResolveCase("com.cyberday1.neoorigins.power.builtin.FlightPower",
                    "FlightPower$Config"),
                // intermediate: extends AbstractActivePower<C> extends PowerType<C>
                new ResolveCase("com.cyberday1.neoorigins.power.builtin.ActiveDashPower",
                    "ActiveDashPower$Config"))) {
            try {
                Class<?> got = PowerConfigClassResolver.resolve(Class.forName(rc.powerClass()));
                if (got == null || !got.getName().endsWith(rc.expectConfig())) {
                    System.out.println("[schema-check] FAIL  resolver(" + rc.powerClass()
                        + ") = " + got + ", expected …" + rc.expectConfig());
                    failures++;
                }
            } catch (ReflectiveOperationException e) {
                System.out.println("[schema-check] FAIL  cannot load " + rc.powerClass() + ": " + e);
                failures++;
            }
        }

        // 6. FormModel schema path + EnumHints enrich: attribute_modifier's
        //    `operation` must render as an ENUM with the JSON token vocabulary.
        try {
            List<FormFieldSpec> form = FormModel.forPower(
                Identifier.fromNamespaceAndPath("neoorigins", "attribute_modifier"));
            FormFieldSpec op = form.stream()
                .filter(s -> s.name().equals("operation")).findFirst().orElse(null);
            if (op == null) {
                System.out.println("[schema-check] FAIL  attribute_modifier form missing 'operation'");
                failures++;
            } else if (op.kind() != FormFieldSpec.Kind.ENUM
                    || !op.enumValues().contains("add_value")) {
                System.out.println("[schema-check] FAIL  'operation' kind=" + op.kind()
                    + " values=" + op.enumValues() + " (expected ENUM incl. add_value)");
                failures++;
            }
        } catch (RuntimeException e) {
            System.out.println("[schema-check] FAIL  FormModel.forPower threw: " + e);
            failures++;
        }

        // 7. Full per-power form coverage: every builtin power must resolve a
        //    Config and either expose fields or be a genuine marker-only power.
        failures += auditPowerFormCoverage(model);

        // 8. Condition/Action picker sources stay in sync with the parsers.
        failures += auditParserTypes(
            "src/main/java/com/cyberday1/neoorigins/compat/condition/ConditionParser.java",
            "condition",
            com.cyberday1.neoorigins.compat.condition.BuiltinConditions.canonicalIds(),
            com.cyberday1.neoorigins.compat.condition.BuiltinConditions.aliasIds());
        failures += auditParserTypes(
            "src/main/java/com/cyberday1/neoorigins/compat/action/ActionParser.java",
            "action",
            com.cyberday1.neoorigins.compat.action.BuiltinActions.canonicalIds(),
            com.cyberday1.neoorigins.compat.action.BuiltinActions.aliasIds());

        // 9. Every form field of every power must have a description.
        failures += auditFieldDocs(model);

        // 9b. BuiltinPowers field-specs must not drift from the power's Config
        //     codec (the power analogue of auditParserTypes). Vacuously green
        //     until powers are registered.
        failures += auditPowerFieldSpecs(model);

        // 10. Action / condition schemas must also be packaged + parse, so the
        //     2.1 RefRow widget can render sub-forms when an entity_action /
        //     condition REF is picked.
        failures += auditAuxSchema(SchemaFormModel.ACTION_RESOURCE_PATH,
            "action", "neoorigins:add_velocity");
        failures += auditAuxSchema(SchemaFormModel.CONDITION_RESOURCE_PATH,
            "condition", "neoorigins:in_water");

        System.out.printf("[schema-check] %d power types, %d structured branches, %d failures%n",
            types, model.structuredTypes().size(), failures);
        if (failures > 0) System.exit(1);
        System.out.println("[schema-check] OK");
    }

    private static int auditAuxSchema(String resource, String label, String sampleId) {
        SchemaFormModel s;
        try {
            s = SchemaFormModel.loadFromClasspath(resource);
        } catch (RuntimeException e) {
            System.out.println("[schema-check] FAIL  " + label + " schema load: " + e.getMessage());
            return 1;
        }
        int branches = s.structuredTypes().size();
        if (branches == 0) {
            System.out.println("[schema-check] FAIL  " + label + " schema parsed 0 structured branches");
            return 1;
        }
        if (!s.hasStructuredForm(sampleId)) {
            System.out.println("[schema-check] FAIL  " + label + " schema missing branch for " + sampleId);
            return 1;
        }
        System.out.printf("[schema-check] %s schema: %d structured branches (sample %s OK)%n",
            label, branches, sampleId);
        return 0;
    }

    /**
     * Re-derives the parser's {@code case "<ns>:<name>"} labels from its source
     * (canonicalised to {@code neoorigins:<name>}, as the parser does at
     * runtime) and asserts they exactly equal the parser's {@code KNOWN_TYPES}
     * set — so the creator's picker can never silently drift from what the
     * parser actually accepts. Returns the failure count.
     */
    private static int auditParserTypes(String src, String label, java.util.Set<String> descriptorIds) {
        return auditParserTypes(src, label, descriptorIds, java.util.Set.of());
    }

    /**
     * @param descriptorIds canonical migrated descriptor ids (each a distinct
     *                       registry type that must appear in {@code KNOWN_TYPES}).
     * @param aliasIds       alias ids — known synonyms that dispatch to a canonical
     *                       verb. These are handled by the parser but are
     *                       deliberately <em>not</em> separate types, so they are
     *                       excluded from the {@code KNOWN_TYPES} parity check on
     *                       both sides: their absence from {@code KNOWN_TYPES} is
     *                       not a "missing arm", and a switch/case label for them
     *                       is not an "unhandled id". The reported type total is
     *                       {@code KNOWN_TYPES}'s size — aliases never inflate it.
     */
    private static int auditParserTypes(String src, String label,
                                        java.util.Set<String> descriptorIds,
                                        java.util.Set<String> aliasIds) {
        String text;
        try {
            text = java.nio.file.Files.readString(java.nio.file.Path.of(src));
        } catch (java.io.IOException e) {
            System.out.println("[schema-check] FAIL  cannot read " + src + ": " + e);
            return 1;
        }
        // Verbs the parser actually handles = remaining switch arms (canonicalised
        // to neoorigins:<name> like the parser does) ∪ migrated descriptor ids.
        // As the registry refactor moves verbs off the switch onto registered
        // descriptors, KNOWN_TYPES stays put while the case arm disappears — the
        // descriptor set restores that verb here so the parity assertion holds.
        //
        // A switch arm may carry MULTIPLE labels: `case "a",\n "b" -> …`, where the
        // trailing labels are synonyms dispatching to the same handler (e.g.
        // ConditionParser's `case "neoorigins:xp_level",\n "neoorigins:xp_levels"`).
        // Two distinct sets matter for parity:
        //   • `primary`  — the FIRST label of each arm, plus migrated descriptor
        //     ids. Every primary verb is a distinct picker entry, so each MUST be
        //     in KNOWN_TYPES (the `missing` check).
        //   • `handled`  — every label of every arm (primaries + synonyms). Nothing
        //     in KNOWN_TYPES may name an id no arm handles (the `extra` check), but
        //     a synonym appearing here lets a KNOWN_TYPES entry like `xp_levels`
        //     be recognised as handled without forcing every synonym into the set.
        // Matching the whole `case … ->` span (DOTALL) — rather than `case "…"`
        // alone — is what lets continuation labels register as handled; scoping to
        // that span avoids scooping up unrelated quoted ids (KNOWN_TYPES literal,
        // parse-helper string args), keeping the parity check strict.
        java.util.Set<String> primary = new java.util.TreeSet<>(descriptorIds);
        java.util.Set<String> handled = new java.util.TreeSet<>(descriptorIds);
        java.util.regex.Matcher arm = java.util.regex.Pattern
            .compile("case\\s+(\"[a-z_]+:[a-z_]+\"(?:\\s*,\\s*\"[a-z_]+:[a-z_]+\")*)\\s*->",
                java.util.regex.Pattern.DOTALL).matcher(text);
        java.util.regex.Pattern labelPat = java.util.regex.Pattern.compile("\"[a-z_]+:([a-z_]+)\"");
        while (arm.find()) {
            java.util.regex.Matcher lbl = labelPat.matcher(arm.group(1));
            boolean first = true;
            while (lbl.find()) {
                String id = "neoorigins:" + lbl.group(1);
                handled.add(id);
                if (first) { primary.add(id); first = false; }
            }
        }

        // The declared KNOWN_TYPES = Set.of( … ) literal block.
        java.util.Set<String> declared = new java.util.TreeSet<>();
        int s = text.indexOf("KNOWN_TYPES");
        int open = s < 0 ? -1 : text.indexOf("Set.of(", s);
        int close = open < 0 ? -1 : text.indexOf(");", open);
        if (close > 0) {
            java.util.regex.Matcher dm = java.util.regex.Pattern
                .compile("\"(neoorigins:[a-z_]+)\"").matcher(text.substring(open, close));
            while (dm.find()) declared.add(dm.group(1));
        }

        // Alias ids are known synonyms, not separate types: drop them from every
        // parity set so an alias never has to live in KNOWN_TYPES (and a leftover
        // alias case label is not flagged as unhandled).
        primary.removeAll(aliasIds);
        handled.removeAll(aliasIds);
        declared.removeAll(aliasIds);

        // Every primary verb must be declared; nothing declared may be unhandled.
        // (A KNOWN_TYPES synonym like xp_levels is in `handled` but not `primary`,
        // so it satisfies the `extra` check without inflating `missing`.)
        java.util.Set<String> missing = new java.util.TreeSet<>(primary);
        missing.removeAll(declared);
        java.util.Set<String> extra = new java.util.TreeSet<>(declared);
        extra.removeAll(handled);
        int fails = 0;
        if (declared.isEmpty()) {
            System.out.println("[schema-check] FAIL  " + label
                + " KNOWN_TYPES literal not found in " + src);
            fails++;
        }
        if (!missing.isEmpty()) {
            System.out.println("[schema-check] FAIL  " + label
                + " KNOWN_TYPES missing switch arms: " + missing);
            fails++;
        }
        if (!extra.isEmpty()) {
            System.out.println("[schema-check] FAIL  " + label
                + " KNOWN_TYPES has ids the switch doesn't handle: " + extra);
            fails++;
        }
        if (fails == 0) {
            System.out.printf("[schema-check] %s picker: %d types, in sync with switch+descriptors%n",
                label, declared.size());
        }
        return fails;
    }

    /**
     * Enumerates every concrete {@code *Power} class in {@code power.builtin},
     * resolves its {@code Config} the way the creator does, and proves the form
     * is thorough: a missing/unresolvable Config is a hard failure; a power
     * with zero fields is reported as marker-only (its empty form is correct
     * and intentional — nothing to configure). Origins and classes share this
     * exact path (a class is just an origin in the class layer), so this
     * covers both. Returns the failure count.
     */
    private static int auditPowerFormCoverage(SchemaFormModel model) {
        int fails = 0;
        java.io.File dir;
        try {
            java.io.File root = new java.io.File(SchemaFormCheck.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
            dir = new java.io.File(root, "com/cyberday1/neoorigins/power/builtin");
        } catch (Exception e) {
            System.out.println("[schema-check] FAIL  cannot locate builtin classes: " + e);
            return 1;
        }
        java.io.File[] files = dir.listFiles((d, n) ->
            n.endsWith("Power.class") && !n.contains("$"));
        if (files == null || files.length == 0) {
            System.out.println("[schema-check] FAIL  no builtin power classes found at " + dir);
            return 1;
        }
        java.util.Arrays.sort(files, java.util.Comparator.comparing(java.io.File::getName));

        int total = 0, withFields = 0, schemaBacked = 0;
        java.util.List<String> markerOnly = new java.util.ArrayList<>();
        java.util.Set<String> structured = model.structuredTypes();

        for (java.io.File f : files) {
            String cn = "com.cyberday1.neoorigins.power.builtin."
                + f.getName().substring(0, f.getName().length() - 6);
            Class<?> pc;
            try {
                // initialize=false: reflection on structure/records doesn't need
                // static init, and some powers' static fields touch MC bootstrap
                // that isn't available headless (would be a false failure).
                pc = Class.forName(cn, false, SchemaFormCheck.class.getClassLoader());
            } catch (Throwable t) {
                System.out.println("[schema-check] FAIL  load " + cn + ": " + t);
                fails++;
                continue;
            }
            if (java.lang.reflect.Modifier.isAbstract(pc.getModifiers())
                    || !com.cyberday1.neoorigins.api.power.PowerType.class.isAssignableFrom(pc)) {
                continue; // abstract base / non-power
            }
            total++;
            Class<?> cfg = com.cyberday1.neoorigins.power.schemaform
                .PowerConfigClassResolver.resolve(pc);
            if (cfg == null || !cfg.isRecord()) {
                System.out.println("[schema-check] FAIL  " + pc.getSimpleName()
                    + ": no Config record resolved (form would be permanently empty)");
                fails++;
                continue;
            }
            int n = CodecFieldSpecExtractor.extract(cfg).size();
            if (n > 0) withFields++;
            else markerOnly.add(pc.getSimpleName());
        }
        for (String t : structured) if (t.startsWith("neoorigins:")) schemaBacked++;

        System.out.printf(
            "[schema-check] coverage: %d builtin powers - %d with fields, %d marker-only, "
            + "%d schema-enriched%n", total, withFields, markerOnly.size(), schemaBacked);
        System.out.println("[schema-check] marker-only (empty form is correct - no config): "
            + String.join(", ", markerOnly));
        return fails;
    }

    /**
     * Drift guard for {@link com.cyberday1.neoorigins.power.registry.BuiltinPowers}
     * — the power analogue of {@link #auditParserTypes}. Powers deserialize
     * through their own {@code Codec<Config>} (untouched by the refactor); this
     * proves the declarative {@code FieldSpec} metadata stays consistent with
     * that codec's record shape, so the schema / doc / editor layer can never
     * silently drift from what a power actually parses.
     *
     * <p>For every power registered in {@code BuiltinPowers}, resolves its
     * {@code Config} record (via {@code PowerConfigClassResolver}, headless-safe
     * off the carried {@code Class}) and asserts:
     * <ol type="a">
     *   <li>every {@code FieldSpec.name} maps to a record component
     *       (camel→snake) — no spec for a field the codec doesn't have;</li>
     *   <li>every non-internal record component has a spec OR is covered by the
     *       reflection fallback (so dropping a field from the spec without a
     *       reason is caught) — reported, not failed, since the fallback is a
     *       legitimate safety net;</li>
     *   <li>required-ness matches {@code Optional}-typedness wherever the codec
     *       exposes it: an {@code Optional<>} component must not be modelled
     *       {@code required(true)} (the only direction reflection can prove);</li>
     *   <li>if a {@code power.schema.json} branch exists for the type, the spec
     *       is consistent with it — every schema field name appears in the spec.</li>
     * </ol>
     *
     * <p>Vacuously green with nothing registered. For marker-only powers (empty
     * spec, {@code Config(String type)}) every clause holds trivially. Returns
     * the failure count.
     */
    private static int auditPowerFieldSpecs(SchemaFormModel model) {
        int fails = 0;
        var descriptors = com.cyberday1.neoorigins.power.registry.BuiltinPowers.descriptors();
        java.util.List<String> internal = List.of("type", "powerId");

        for (var entry : descriptors.entrySet()) {
            Identifier id = entry.getKey();
            var spec = entry.getValue();
            String typeId = id.toString();

            Class<?> cfg = com.cyberday1.neoorigins.power.schemaform
                .PowerConfigClassResolver.resolve(spec.powerClass());
            if (cfg == null || !cfg.isRecord()) {
                System.out.println("[schema-check] FAIL  power-spec " + typeId
                    + ": no Config record resolved from " + spec.powerClass().getSimpleName());
                fails++;
                continue;
            }

            // Map snake-case JSON key → record component, for the declared specs.
            java.util.Map<String, java.lang.reflect.RecordComponent> byJson =
                new java.util.LinkedHashMap<>();
            for (java.lang.reflect.RecordComponent rc : cfg.getRecordComponents()) {
                byJson.put(camelToSnake(rc.getName()), rc);
            }

            // JSON key names (for the schema-branch parity check, clause d) and
            // the resolved component snake-keys (for component existence/coverage,
            // clauses a/b). They differ only for key-aliased specs: a FieldSpec
            // whose JSON `name` (e.g. entity_action) is bound via `.boundTo(..)`
            // to a differently-named component (e.g. action) — see
            // FieldSpec.effectiveComponentName.
            java.util.Set<String> declaredNames = new java.util.TreeSet<>();
            java.util.Set<String> declaredComponents = new java.util.TreeSet<>();
            for (com.cyberday1.neoorigins.compat.registry.FieldSpec fs : spec.fields()) {
                declaredNames.add(fs.name());
                fails += auditSpec(fs, typeId, byJson, declaredComponents);
            }

            // (b) every non-internal component should have a spec OR rely on the
            //     reflection fallback. Only meaningful once the power declares at
            //     least one field (an all-empty spec IS the marker/fallback case).
            if (!spec.fields().isEmpty()) {
                for (var jc : byJson.entrySet()) {
                    String compName = jc.getValue().getName();
                    if (internal.contains(compName)) continue;
                    // Match on the resolved component snake-key (clause a), so a
                    // key-aliased spec covers its component even though its JSON
                    // name differs.
                    if (!declaredComponents.contains(jc.getKey())) {
                        System.out.println("[schema-check] WARN  power-spec " + typeId
                            + ": component '" + jc.getKey() + "' has no FieldSpec"
                            + " (relying on codec-reflection fallback)");
                    }
                }
            }

            // (d) schema branch (when present) must be covered by the spec — but
            //     only check this once the power declares fields (an empty spec is
            //     the not-yet-migrated/marker case and falls back to the schema).
            if (!spec.fields().isEmpty() && model.hasStructuredForm(typeId)) {
                for (FormFieldSpec sf : model.formFor(typeId)) {
                    if (sf.name().equals("type")) continue;
                    if (!declaredNames.contains(sf.name())) {
                        System.out.println("[schema-check] FAIL  power-spec " + typeId
                            + ": schema branch field '" + sf.name()
                            + "' is missing from the FieldSpec list");
                        fails++;
                    }
                }
            }
        }

        System.out.printf("[schema-check] power field-specs: %d registered powers, %d failures%n",
            descriptors.size(), fails);
        return fails;
    }

    /**
     * Audit a single {@link com.cyberday1.neoorigins.compat.registry.FieldSpec}
     * against the given {@code byJson} component map, recording every resolved
     * component snake-key into {@code declaredComponents} (so clause (b) sees it
     * as covered). Returns the failure count contributed by this spec.
     *
     * <p>Three shapes:
     * <ul>
     *   <li><b>Virtual wrapper</b> ({@code fs.isVirtual()}): the wrapper itself
     *       has NO backing component — skip clauses (a)/(c) for it and recurse
     *       into its children against the SAME top-level {@code byJson} map
     *       (children map to flat components via {@code .boundTo}).</li>
     *   <li><b>Real OBJECT with children</b>: resolve the wrapper's own component
     *       (clause a), then recurse children against that component-record's own
     *       component map.</li>
     *   <li><b>Leaf</b>: clauses (a) and (c), unchanged.</li>
     * </ul>
     */
    private static int auditSpec(com.cyberday1.neoorigins.compat.registry.FieldSpec fs,
                                 String typeId,
                                 java.util.Map<String, java.lang.reflect.RecordComponent> byJson,
                                 java.util.Set<String> declaredComponents) {
        int fails = 0;

        // Virtual wrapper: no own component. Skip (a)/(c) for the wrapper; recurse
        // children against the SAME top-level component map.
        if (fs.isVirtual()) {
            for (var child : fs.children()) {
                fails += auditSpec(child, typeId, byJson, declaredComponents);
            }
            return fails;
        }

        String componentKey = camelToSnake(fs.effectiveComponentName());
        declaredComponents.add(componentKey);

        // (a) the spec's field must map to a real record component — resolved via
        // the key-alias (effectiveComponentName) when set, else by camel→snake-ing
        // the JSON name itself.
        java.lang.reflect.RecordComponent rc = byJson.get(componentKey);
        if (rc == null) {
            System.out.println("[schema-check] FAIL  power-spec " + typeId
                + ": field '" + fs.name() + "' has no matching Config component"
                + (fs.componentName() != null ? " (bound to '" + fs.componentName() + "')" : ""));
            return fails + 1;
        }
        // (c) Optional-typed component must not be modelled required.
        boolean optionalTyped = rc.getType() == java.util.Optional.class;
        if (optionalTyped && fs.required()) {
            System.out.println("[schema-check] FAIL  power-spec " + typeId
                + ": field '" + fs.name() + "' is Optional<> in the codec but"
                + " declared required(true)");
            fails++;
        }

        // Real OBJECT with children: recurse into the sub-record's own component
        // map. (Forward-compat; resource uses the virtual path above.)
        if (!fs.children().isEmpty()) {
            Class<?> sub = rc.getType();
            if (sub.isRecord()) {
                java.util.Map<String, java.lang.reflect.RecordComponent> subByJson =
                    new java.util.LinkedHashMap<>();
                for (java.lang.reflect.RecordComponent src : sub.getRecordComponents()) {
                    subByJson.put(camelToSnake(src.getName()), src);
                }
                java.util.Set<String> subDeclared = new java.util.TreeSet<>();
                for (var child : fs.children()) {
                    fails += auditSpec(child, typeId, subByJson, subDeclared);
                }
            }
        }
        return fails;
    }

    /** camelCase → snake_case, mirroring {@code CodecFieldSpecExtractor.camelToSnake}. */
    private static String camelToSnake(String s) {
        StringBuilder b = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (i > 0) b.append('_');
                b.append(Character.toLowerCase(ch));
            } else {
                b.append(ch);
            }
        }
        return b.toString();
    }

    /**
     * Enforces that every form field of every registered power has a
     * description — from the schema branch or from {@code field_docs.json}.
     * Fails the build listing every undocumented {@code type.field} so the
     * docs can never silently regress or stay incomplete.
     */
    private static int auditFieldDocs(SchemaFormModel model) {
        String src;
        try {
            src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/cyberday1/neoorigins/power/registry/PowerTypes.java"));
        } catch (java.io.IOException e) {
            System.out.println("[schema-check] FAIL  cannot read PowerTypes.java: " + e);
            return 1;
        }
        com.cyberday1.neoorigins.power.schemaform.FieldDocs docs =
            com.cyberday1.neoorigins.power.schemaform.FieldDocs.get();

        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("reg\\(\\s*\"([a-z0-9_]+)\"\\s*,\\s*new\\s+([\\w.]+)\\s*\\(")
            .matcher(src);
        int totalFields = 0, documented = 0;
        java.util.List<String> undoc = new java.util.ArrayList<>();

        while (m.find()) {
            String id = m.group(1);
            String ref = m.group(2);
            String simple = ref.substring(ref.lastIndexOf('.') + 1);
            Class<?> pc = null;
            for (String fqn : new String[]{
                    ref.contains(".") ? ref : null,
                    "com.cyberday1.neoorigins.power.builtin." + simple,
                    "com.cyberday1.neoorigins.compat." + simple}) {
                if (fqn == null) continue;
                try { pc = Class.forName(fqn, false, SchemaFormCheck.class.getClassLoader()); break; }
                catch (Throwable ignored) { }
            }
            if (pc == null
                    || !com.cyberday1.neoorigins.api.power.PowerType.class.isAssignableFrom(pc)) {
                continue;
            }
            String typeId = "neoorigins:" + id;

            // A power registered in BuiltinPowers carries its field docs ON the
            // FieldSpec (D2: doc lives on the spec that drives the form), so it
            // is fully spec-driven — its field_docs.json entry has been
            // collapsed. Source descriptions from the spec for those fields, so
            // this gate keeps proving "every form field is documented" without
            // the side-table. Non-registered powers fall back to field_docs.json.
            java.util.Map<String, String> specDocs = new java.util.HashMap<>();
            java.util.List<com.cyberday1.neoorigins.compat.registry.FieldSpec> declared =
                com.cyberday1.neoorigins.power.registry.BuiltinPowers.fieldsFor(typeId);
            if (declared != null) {
                for (var fs : declared) {
                    if (fs.description() != null && !fs.description().isBlank()) {
                        specDocs.put(fs.name(), fs.description());
                        // Also index by the resolved component snake-key so a
                        // key-aliased field's doc is found whether the form
                        // surfaces the author-facing JSON name (FormModel) or the
                        // raw component name (the reflection fallback used here
                        // once the schema branch is collapsed). For un-aliased
                        // fields this key equals fs.name() — a harmless re-put.
                        specDocs.put(camelToSnake(fs.effectiveComponentName()), fs.description());
                    }
                }
            }

            java.util.List<FormFieldSpec> specs;
            boolean schemaBranch = model.hasStructuredForm(typeId);
            if (schemaBranch) {
                specs = model.formFor(typeId);
            } else {
                Class<?> cfg = com.cyberday1.neoorigins.power.schemaform
                    .PowerConfigClassResolver.resolve(pc);
                if (cfg == null || !cfg.isRecord()) continue; // marker-only
                specs = CodecFieldSpecExtractor.extract(cfg);
            }
            for (FormFieldSpec s : specs) {
                if (s.name().equals("type")) continue;
                totalFields++;
                boolean ok = specDocs.containsKey(s.name())
                    || (schemaBranch && s.description() != null
                        && !s.description().isBlank())
                    || (docs.describe(typeId, s.name()) != null
                        && !docs.describe(typeId, s.name()).isBlank());
                if (ok) documented++;
                else undoc.add(typeId + "." + s.name());
            }
        }
        java.util.Collections.sort(undoc);
        System.out.printf("[schema-check] field docs: %d/%d documented%n",
            documented, totalFields);
        if (!undoc.isEmpty()) {
            System.out.println("[schema-check] FAIL  " + undoc.size()
                + " undocumented fields:");
            for (String u : undoc) System.out.println("    " + u);
            return 1;
        }
        return 0;
    }
}
