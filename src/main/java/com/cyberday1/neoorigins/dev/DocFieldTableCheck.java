package com.cyberday1.neoorigins.dev;

import com.cyberday1.neoorigins.compat.OriginsMultipleExpander;
import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.power.registry.BuiltinPowers;
import com.cyberday1.neoorigins.power.registry.LegacyAliasPowerSpecs;
import com.cyberday1.neoorigins.power.registry.LegacyPowerTypeAliases;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Report on the one hop in the authoring pipeline that nothing checks: the
 * <b>doc reference tables</b> in {@code docs/POWER_TYPES.md} against the
 * {@code FieldSpec} registry they are supposed to describe.
 *
 * <p>Three layers exist and only two were pinned. {@code schemaDriftVerify}
 * proves <i>generated schema == FieldSpec</i>, and
 * {@code SchemaFormCheck.auditPowerFieldSpecs} proves one direction of
 * <i>FieldSpec == codec</i>. The prose tables an author actually reads were
 * hand-maintained against neither, which is how a section came to claim "No
 * additional fields beyond name and description" for a power that registers
 * several, and how registered fields came to be documented nowhere.
 *
 * <p><b>Report, not a gate.</b> The two sides are not expected to match 1:1 and
 * a wholesale failure would say nothing useful, so this exits zero always. What
 * it produces is a triaged count, bucketed so systemic gaps do not drown
 * per-power ones:
 *
 * <ol>
 *   <li><b>Registry-only</b> — the field parses but no doc table row mentions
 *       it. An author cannot discover it.</li>
 *   <li><b>Doc-only</b> — a row promises a field the registry does not declare.
 *       Either the doc is stale or a {@code FieldSpec} is missing.</li>
 *   <li><b>Required mismatch</b> — the table's Required column disagrees with
 *       {@link FieldSpec#required()}. The schema and both editors follow the
 *       registry, so the doc is the liar by construction, but the pair is worth
 *       eyeballing because it can equally mean the spec is wrong.</li>
 *   <li><b>False "No additional fields"</b> — the section states the power has
 *       none and the registry declares some. Strictly a doc defect.</li>
 *   <li><b>Coverage</b> — ids documented with no registry entry, and registered
 *       ids with no {@code ##} section at all.</li>
 * </ol>
 *
 * <p>Buckets 1 and 2 are split again by how many distinct power ids a given
 * field name misses on. A name missing on many ids is a SYSTEMIC omission — a
 * shared spec constant such as {@code always_show_icon} or {@code key} that was
 * added to the registry once and to no doc table — and is one decision, not
 * thirty defects. Anything under {@link #SYSTEMIC_MIN} is a per-power defect and
 * is listed individually. That split is computed from the data rather than
 * hardcoded, so a constant that later does get documented drops out on its own.
 *
 * <p>Only the FIRST field table in each section is read: sub-object tables
 * always follow a bolded lead-in and describe a nested shape, which the
 * top-level {@code FieldSpec} list does not contain. Header columns are resolved
 * by name, not position, because five header shapes are in use.
 *
 * <p>Run: {@code ./gradlew docFieldTableCheck}.
 */
public final class DocFieldTableCheck {

    private DocFieldTableCheck() {}

    private static final Path DOC = Path.of("docs/POWER_TYPES.md");

    /** {@code ## `neoorigins:foo`} — a power section heading (exactly two hashes). */
    private static final Pattern SECTION =
        Pattern.compile("^##\\s+`([a-z0-9_.-]+:[a-z0-9_./-]+)`\\s*$");

    /** A doc row's field cell must be a bare JSON key to be comparable. */
    private static final Pattern FIELD_NAME = Pattern.compile("^[a-z0-9_]+$");

    /** Display fields every power carries; never in a FieldSpec list, always in prose. */
    private static final Set<String> UNIVERSAL = Set.of("name", "description");

    /** Distinct-id count at which a one-sided field name is a systemic omission, not a defect. */
    private static final int SYSTEMIC_MIN = 5;

    /**
     * Ids whose {@code power.schema.json} branch is hand-written and spliced in by
     * {@code PowerSchemaGenerator} rather than generated from FieldSpecs, so there is
     * no registry side to diff their doc table against.
     *
     * <p>Kept deliberately short. {@code starting_equipment} was on this list until it
     * turned out to be perfectly representable (ARRAY {@code children} covers its
     * nested stacks/enchantments) and got a real descriptor. {@code particle} is not:
     * its {@code particle} key is a {@code oneOf} whose object arm has its own
     * {@code properties}/{@code required}, and {@code spread}/{@code offset} are
     * {@code minItems}/{@code maxItems}-pinned vec3s — none of which
     * {@code SchemaNodeBuilder} can emit. Give FieldSpec those and this list empties.
     */
    private static final List<String> SPLICED_BRANCH_IDS = List.of("neoorigins:particle");

    /**
     * Namespaces the compat surface expands an {@code origins:} id over
     * ({@code OriginsFormatDetector.legacyPowerTypeSurface}). The doc writes one
     * {@code origins:} section and says the family spelling is equivalent, so a
     * family id resolves to its {@code origins:} sibling's section rather than
     * counting as undocumented.
     */
    private static final Set<String> FAMILY_NS = Set.of("apoli", "apugli", "apace");

    /** Heading line carrying a backticked id: a doc anchor, even without a dedicated section. */
    private static final Pattern HEADING_ID =
        Pattern.compile("^#{1,6}\\s+.*`([a-z0-9_.-]+:[a-z0-9_./-]+)`");

    /**
     * Marker introducing a SHARED FIELD CLAIM: a passage that documents one or
     * more fields once, for a hand-written list of power types, instead of
     * repeating the row in each type's own table. The marker names the fields;
     * the power ids are the backticked bare names in the passage that follows.
     *
     * <p>Without this the tool would report every such field as undocumented on
     * every listed type, which is both wrong and loud enough to bury the real
     * findings. With it the enumeration itself becomes the checked thing, which
     * is the part that was never pinned: the doc's list of types is a hand
     * transcription of a set the code derives at runtime, and a stale entry is a
     * correctness bug, not a formatting one. An author reading that a type
     * claims {@code condition} for its own config, when it no longer does,
     * writes {@code power_condition} and silently gets a different gate.
     */
    private static final Pattern SHARED_MARKER =
        Pattern.compile("^<!--\\s*shared-fields:\\s*([a-z0-9_,\\s]+?)\\s*-->$");

    /** A backticked bare identifier inside a shared-claim passage: a candidate power name. */
    private static final Pattern BACKTICKED_NAME = Pattern.compile("`([a-z0-9_]+)`");

    /** Stand-in for {@code \|} so an escaped pipe does not split a cell. */
    private static final String PIPE_ESCAPE = "\u0000PIPE\u0000";

    /** One power's documented surface: its first field table, plus the marker-only claim. */
    private record DocSection(String id, int line, Map<String, DocRow> rows,
                              boolean claimsNoAdditional, boolean hasTable, int skippedRows) {}

    /** One row of a doc field table, columns resolved by header name. */
    private record DocRow(String field, Boolean required, String defaultValue, int line) {}

    /** One {@code <!-- shared-fields: … -->} passage: fields documented once for a list of ids. */
    private record SharedClaim(List<String> fields, Set<String> ids, int line) {}

    public static void main(String[] args) throws IOException {
        boolean emitRows = List.of(args).contains("--rows");
        // The alias table is populated by an explicit call, not by class load.
        LegacyPowerTypeAliases.bootstrap();
        List<String> lines = Files.readAllLines(DOC, StandardCharsets.UTF_8);
        Map<String, DocSection> docs = parseSections(lines);
        Map<String, List<FieldSpec>> registry = registry();

        System.out.println("[doc-fields] " + docs.size() + " documented sections, "
            + registry.size() + " registered descriptors, "
            + docs.values().stream().mapToInt(s -> s.rows().size()).sum() + " comparable doc rows, "
            + registry.values().stream().mapToInt(List::size).sum() + " FieldSpec declarations");

        int skipped = docs.values().stream().mapToInt(DocSection::skippedRows).sum();
        if (skipped > 0) {
            System.out.println("[doc-fields] " + skipped
                + " doc rows skipped: field cell is not a bare JSON key (prose rows, "
                + "combined `name` / `description` rows, `<subkey>` placeholders)");
        }

        // ---- shared field claims -------------------------------------------
        List<SharedClaim> claims = parseSharedClaims(lines, registry.keySet());
        Map<String, Set<String>> claimedIds = new TreeMap<>();  // field -> ids the doc claims
        for (SharedClaim c : claims) {
            for (String f : c.fields()) {
                claimedIds.computeIfAbsent(f, k -> new TreeSet<>()).addAll(c.ids());
            }
        }
        System.out.println("[doc-fields] " + claims.size() + " shared-field claims covering "
            + claimedIds.size() + " field names");

        List<String> claimDrift = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : claimedIds.entrySet()) {
            // Only NATIVE ids are in scope. An alias id's fields are parsed by
            // its remap target (PowerDataManager.resolvePowerType remaps before
            // any field is read), so the target's entry in the list is what
            // covers it; listing the alias too would be a second name for one
            // fact and would go stale independently.
            Set<String> declared = new TreeSet<>();
            for (Map.Entry<String, List<FieldSpec>> r : registry.entrySet()) {
                if (!BuiltinPowers.isRegistered(ResourceLocation.parse(r.getKey()))) continue;
                if (r.getValue().stream().anyMatch(f -> f.name().equals(e.getKey()))) {
                    declared.add(r.getKey());
                }
            }
            Set<String> missingFromDoc = new TreeSet<>(declared);
            missingFromDoc.removeAll(e.getValue());
            Set<String> staleInDoc = new TreeSet<>(e.getValue());
            staleInDoc.removeAll(declared);
            for (String id : missingFromDoc) {
                claimDrift.add(e.getKey() + ": " + id + " declares it, the doc's list omits it");
            }
            for (String id : staleInDoc) {
                claimDrift.add(e.getKey() + ": the doc lists " + id + ", which does not declare it");
            }
        }

        // ---- coverage -------------------------------------------------------
        Set<String> anchored = headingIds(lines);
        Set<String> docOnlyIds = new TreeSet<>(docs.keySet());
        docOnlyIds.removeAll(registry.keySet());
        // Container types carry no fields of their own — they hold a `powers`
        // list that the expander splits into real powers before any spec is
        // consulted. PowerEnumCheck exempts the same set for the same reason.
        docOnlyIds.removeAll(OriginsMultipleExpander.MULTIPLE_TYPES);
        // The one branch PowerSchemaGenerator still splices verbatim out of the
        // committed schema, so there is no FieldSpec table to diff its doc against.
        // Its shape is genuinely beyond SchemaNodeBuilder: `particle` is a oneOf
        // whose object arm carries its own properties/required, and spread/offset
        // are minItems/maxItems-pinned vec3s. Until FieldSpec can express those,
        // the particle doc table is ungated — a real gap, named rather than hidden.
        List<String> staleExemptions = new ArrayList<>();
        for (String id : SPLICED_BRANCH_IDS) {
            // Verify the exception still holds rather than trusting it: once the id
            // gains a descriptor it is diffable and the exemption must come out.
            if (!docOnlyIds.remove(id)) {
                staleExemptions.add(id + "  (now has a FieldSpec table \u2014 drop it from "
                    + "SPLICED_BRANCH_IDS so its doc table is diffed)");
            }
        }

        Set<String> regOnlyIds = new TreeSet<>();
        List<String> anchorOnly = new ArrayList<>();
        for (String id : new TreeSet<>(registry.keySet())) {
            if (sectionFor(docs, id) != null) continue;
            if (anchored.contains(id)) {
                anchorOnly.add(id + "  (named in a heading, but has no `## \u0060id\u0060` section "
                    + "and so no field table)");
            } else {
                regOnlyIds.add(id);
            }
        }

        // ---- per-field diff -------------------------------------------------
        Map<String, List<String>> registryOnly = new TreeMap<>();   // field -> [ids]
        Map<String, List<String>> docOnly = new TreeMap<>();        // field -> [ids]
        List<String> requiredMismatch = new ArrayList<>();
        List<String> falseMarkerOnly = new ArrayList<>();
        List<String> untabled = new ArrayList<>();
        /** Section id -> the specs it does not document, for {@code --rows}. */
        Map<String, List<FieldSpec>> undocumented = new LinkedHashMap<>();

        Set<String> diffed = new TreeSet<>();
        for (String regId : new TreeSet<>(registry.keySet())) {
            DocSection sec = sectionFor(docs, regId);
            if (sec == null) continue;
            // A family id shares its origins: sibling's section; diff that set
            // once, and report it under the id the doc actually writes.
            if (!diffed.add(sec.id())) continue;
            String id = sec.id();

            List<FieldSpec> specs = registry.get(regId);
            Map<String, FieldSpec> byName = new LinkedHashMap<>();
            for (FieldSpec f : specs) byName.put(f.name(), f);

            if (sec.claimsNoAdditional() && !specs.isEmpty()) {
                falseMarkerOnly.add(id + "  (doc line " + sec.line() + " says none; registry declares "
                    + specs.size() + ": " + String.join(", ", byName.keySet()) + ")");
                undocumented.put(id, specs);
                continue; // the whole section is the defect; do not also list every field
            }
            if (!sec.hasTable() && !sec.claimsNoAdditional() && !specs.isEmpty()) {
                untabled.add(id + "  (doc line " + sec.line() + " has no field table; registry declares "
                    + specs.size() + ")");
                undocumented.put(id, specs);
                continue;
            }

            for (Map.Entry<String, FieldSpec> e : byName.entrySet()) {
                if (UNIVERSAL.contains(e.getKey())) continue;
                DocRow row = sec.rows().get(e.getKey());
                if (row == null) {
                    // Documented once for a list of types rather than per table:
                    // the enumeration is checked separately, above. An alias is
                    // covered by its remap target's entry in that list, since
                    // the target is what parses the field.
                    Set<String> claimed = claimedIds.getOrDefault(e.getKey(), Set.of());
                    if (claimed.contains(id) || claimed.contains(aliasTargetOf(id))) continue;
                    registryOnly.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(id);
                    undocumented.computeIfAbsent(id, k -> new ArrayList<>()).add(e.getValue());
                    continue;
                }
                if (row.required() != null && row.required() != e.getValue().required()) {
                    requiredMismatch.add(id + "." + e.getKey() + "  doc(line " + row.line() + ")="
                        + (row.required() ? "yes" : "no") + " registry="
                        + (e.getValue().required() ? "yes" : "no"));
                }
            }
            for (DocRow row : sec.rows().values()) {
                if (UNIVERSAL.contains(row.field())) continue;
                if (!byName.containsKey(row.field())) {
                    docOnly.computeIfAbsent(row.field(), k -> new ArrayList<>()).add(id);
                }
            }
        }

        // ---- report ---------------------------------------------------------
        int defects = 0;

        defects += reportList("SHARED-CLAIM DRIFT (the doc's hand-written type list vs the registry)",
            claimDrift);
        defects += reportOneSided("REGISTRY-ONLY: parses, but no doc row mentions it", registryOnly);
        defects += reportOneSided("DOC-ONLY: a doc row promises it, but no FieldSpec declares it", docOnly);
        defects += reportList("FALSE \"No additional fields\"", falseMarkerOnly);
        defects += reportList("SECTION HAS NO FIELD TABLE, but the registry declares fields", untabled);
        defects += reportList("REQUIRED MISMATCH (registry wins; check which side is wrong)", requiredMismatch);
        defects += reportList("DOCUMENTED, NOT REGISTERED (no FieldSpec table for this id)",
            docOnlyIds.stream().toList());
        defects += reportList("REGISTERED, NOT DOCUMENTED (no `## ` section for this id)",
            regOnlyIds.stream().toList());
        defects += reportList("NO DEDICATED SECTION (has a doc anchor, but no field table)", anchorOnly);
        defects += reportList("STALE EXEMPTION (the recorded exception no longer applies)",
            staleExemptions);

        // Not a defect — a standing, explained gap. Printed every run so it stays
        // visible instead of decaying into an unexplained line in this source file.
        if (!SPLICED_BRANCH_IDS.isEmpty()) {
            System.out.println();
            System.out.println("-- UNGATED BY DESIGN: " + String.join(", ", SPLICED_BRANCH_IDS)
                + " (schema branch is hand-written and spliced; no FieldSpec table exists"
                + " to diff the doc against)");
        }

        System.out.println();
        System.out.println("[doc-fields] " + defects + " findings (report only; this task never fails the build)");

        if (emitRows) emitRows(undocumented);
    }

    /**
     * Print, per section, the markdown table rows that would document its
     * undocumented fields, built from the {@link FieldSpec} itself. The point is
     * that the row's type, required-ness, default and text come from the thing
     * that parses the field rather than from a second reading of the code.
     * Descriptions still want a human pass: several spec docs are a paragraph
     * written for a tooltip, which is longer than a table cell should carry.
     */
    private static void emitRows(Map<String, List<FieldSpec>> undocumented) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<FieldSpec>> e : undocumented.entrySet()) {
            sb.append("\n---- ").append(e.getKey()).append(" ----\n");
            for (FieldSpec f : e.getValue()) {
                sb.append("| `").append(f.name()).append("` | ").append(docType(f)).append(" | ")
                  .append(f.required() ? "yes" : "no").append(" | ").append(docDefault(f)).append(" | ")
                  .append(f.description() == null ? "" : f.description().replace("|", "\\|"))
                  .append(" |\n");
            }
        }
        // Written rather than printed: the rows are meant to be pasted back into
        // the doc verbatim, and the console codepage mangles the em dashes.
        Path out = Path.of("build/tmp/docFieldRows.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("[doc-fields] suggested rows written to " + out);
    }

    /** The doc tables' Type vocabulary for a spec's {@code Kind}. */
    private static String docType(FieldSpec f) {
        return switch (f.kind()) {
            case INTEGER -> "int";
            case NUMBER -> "double";
            case BOOLEAN -> "bool";
            case ENUM -> "string";
            case ARRAY -> "array";
            case OBJECT -> "object";
            case REF -> "object";
            case MIXED -> f.mixedTypes().isEmpty() ? "string / object" : String.join(" / ", f.mixedTypes());
            case STRING -> f.pattern() != null ? "Identifier" : "string";
            default -> f.kind().name().toLowerCase(Locale.ROOT);
        };
    }

    /** Backticked literal, or the em-dash the tables use for "no default". */
    private static String docDefault(FieldSpec f) {
        Object d = f.defaultValue();
        if (d == null) return "\u2014";
        if (d instanceof String s) return s.isEmpty() ? "`\"\"`" : "`" + s + "`";
        return "`" + d + "`";
    }

    // ------------------------------------------------------------------ report

    /**
     * Print a one-sided bucket, splitting systemic field names (missing on
     * {@link #SYSTEMIC_MIN}+ distinct ids) from per-power defects. Returns the
     * finding count, counting a systemic name as ONE finding: it is one
     * decision to document, not N independent bugs.
     */
    private static int reportOneSided(String title, Map<String, List<String>> byField) {
        if (byField.isEmpty()) return 0;
        List<Map.Entry<String, List<String>>> systemic = new ArrayList<>();
        List<Map.Entry<String, List<String>>> individual = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : byField.entrySet()) {
            (e.getValue().size() >= SYSTEMIC_MIN ? systemic : individual).add(e);
        }
        systemic.sort(Comparator.comparingInt((Map.Entry<String, List<String>> e) -> -e.getValue().size()));

        int total = byField.values().stream().mapToInt(List::size).sum();
        System.out.println();
        System.out.println("== " + title + " == " + total + " occurrences over "
            + byField.size() + " field names");
        if (!systemic.isEmpty()) {
            System.out.println("  -- systemic (one shared spec, many powers) --");
            for (Map.Entry<String, List<String>> e : systemic) {
                System.out.println("  " + e.getKey() + "  x" + e.getValue().size()
                    + "  e.g. " + String.join(", ", e.getValue().subList(0, Math.min(3, e.getValue().size()))));
            }
        }
        if (!individual.isEmpty()) {
            System.out.println("  -- per-power --");
            for (Map.Entry<String, List<String>> e : individual) {
                for (String id : e.getValue()) System.out.println("  " + id + "." + e.getKey());
            }
        }
        return systemic.size() + individual.stream().mapToInt(e -> e.getValue().size()).sum();
    }

    private static int reportList(String title, List<String> findings) {
        if (findings.isEmpty()) return 0;
        System.out.println();
        System.out.println("== " + title + " == " + findings.size());
        for (String f : findings) System.out.println("  " + f);
        return findings.size();
    }

    // ------------------------------------------------------------------ inputs

    /** The native type an alias id remaps to, or {@code ""} when it is not an alias. */
    private static String aliasTargetOf(String id) {
        ResourceLocation target = LegacyPowerTypeAliases.aliasTarget(ResourceLocation.parse(id));
        return target == null ? "" : target.toString();
    }

    /** Both hand-maintained spec tables, keyed by canonical id string. */
    private static Map<String, List<FieldSpec>> registry() {
        Map<String, List<FieldSpec>> out = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, BuiltinPowers.PowerSpec> e : BuiltinPowers.descriptors().entrySet()) {
            out.put(e.getKey().toString(), e.getValue().fields());
        }
        for (Map.Entry<ResourceLocation, List<FieldSpec>> e : LegacyAliasPowerSpecs.specs().entrySet()) {
            out.putIfAbsent(e.getKey().toString(), e.getValue());
        }
        return out;
    }

    /**
     * The section describing {@code id}: its own, or for an Apoli-family
     * spelling the {@code origins:} sibling the doc actually writes.
     */
    private static DocSection sectionFor(Map<String, DocSection> docs, String id) {
        DocSection own = docs.get(id);
        if (own != null) return own;
        int colon = id.indexOf(':');
        if (colon < 0 || !FAMILY_NS.contains(id.substring(0, colon))) return null;
        return docs.get("origins:" + id.substring(colon + 1));
    }

    /**
     * Read every {@code <!-- shared-fields: … -->} marker and the passage it
     * introduces. The passage runs to the next blank line; its power ids are the
     * backticked bare names that resolve to a known {@code neoorigins:} id, so
     * incidental backticks in the same sentence ({@code `toggleable`},
     * {@code `cooldown_icon`}) drop out on their own.
     */
    private static List<SharedClaim> parseSharedClaims(List<String> lines, Set<String> knownIds) {
        List<SharedClaim> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = SHARED_MARKER.matcher(lines.get(i).strip());
            if (!m.matches()) continue;
            List<String> fields = new ArrayList<>();
            for (String f : m.group(1).split(",")) {
                String s = f.strip();
                if (!s.isEmpty()) fields.add(s);
            }
            Set<String> ids = new TreeSet<>();
            for (int j = i + 1; j < lines.size(); j++) {
                if (lines.get(j).isBlank()) break;
                Matcher n = BACKTICKED_NAME.matcher(lines.get(j));
                while (n.find()) {
                    String candidate = "neoorigins:" + n.group(1);
                    if (knownIds.contains(candidate)) ids.add(candidate);
                }
            }
            out.add(new SharedClaim(fields, ids, i + 1));
        }
        return out;
    }

    /** Every power id named inside backticks in any heading — a navigable doc anchor. */
    private static Set<String> headingIds(List<String> lines) {
        Set<String> out = new TreeSet<>();
        for (String line : lines) {
            Matcher m = HEADING_ID.matcher(line.strip());
            if (m.find()) out.add(m.group(1));
        }
        return out;
    }

    // ------------------------------------------------------------------ parsing

    private static Map<String, DocSection> parseSections(List<String> lines) {
        Map<String, DocSection> out = new LinkedHashMap<>();
        String id = null;
        int idLine = 0;
        boolean inFence = false;
        boolean tableTaken = false;
        boolean noAdditional = false;
        Map<String, DocRow> rows = new LinkedHashMap<>();
        int skipped = 0;

        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            String line = raw.strip();

            if (line.startsWith("```")) {
                inFence = !inFence;
                continue;
            }
            if (inFence) continue;

            Matcher m = SECTION.matcher(line);
            if (m.matches()) {
                if (id != null) out.put(id, new DocSection(id, idLine, rows, noAdditional, tableTaken, skipped));
                id = m.group(1);
                idLine = i + 1;
                tableTaken = false;
                noAdditional = false;
                rows = new LinkedHashMap<>();
                skipped = 0;
                continue;
            }
            if (id == null) continue;

            // A `## ` heading for something that is not a power ends the section.
            if (line.startsWith("## ")) {
                out.put(id, new DocSection(id, idLine, rows, noAdditional, tableTaken, skipped));
                id = null;
                continue;
            }

            if (line.toLowerCase(Locale.ROOT).startsWith("no additional fields")) {
                noAdditional = true;
                continue;
            }

            if (tableTaken || !line.startsWith("|")) continue;
            List<String> header = cells(line);
            if (header.isEmpty() || !header.get(0).equalsIgnoreCase("Field")) continue;
            if (i + 1 >= lines.size() || !lines.get(i + 1).strip().startsWith("|")) continue;

            int reqCol = indexOf(header, "Required");
            int defCol = indexOf(header, "Default");
            for (int j = i + 2; j < lines.size(); j++) {
                String rowLine = lines.get(j).strip();
                if (!rowLine.startsWith("|")) break;
                List<String> c = cells(rowLine);
                if (c.isEmpty()) break;
                // One row may document a pair of alternative keys in a single
                // cell (`modifier` / `modifiers`); each half is a real key.
                boolean any = false;
                for (String part : c.get(0).split("/")) {
                    String name = part.replace("`", "").strip();
                    if (!FIELD_NAME.matcher(name).matches()) continue;
                    any = true;
                    rows.put(name, new DocRow(name,
                        reqCol >= 0 ? parseRequired(cell(c, reqCol)) : null,
                        defCol >= 0 ? cell(c, defCol).replace("`", "").strip() : null,
                        j + 1));
                }
                if (!any) skipped++;
            }
            tableTaken = true;
        }
        if (id != null) out.put(id, new DocSection(id, idLine, rows, noAdditional, tableTaken, skipped));
        return out;
    }

    private static List<String> cells(String line) {
        String s = line.replace("\\|", PIPE_ESCAPE);
        String[] parts = s.split("\\|", -1);
        List<String> out = new ArrayList<>();
        // A markdown row is bounded by pipes, so the first and last splits are empty padding.
        for (int i = 1; i < parts.length - 1; i++) out.add(parts[i].replace(PIPE_ESCAPE, "|").strip());
        return out;
    }

    private static String cell(List<String> cells, int idx) {
        return idx >= 0 && idx < cells.size() ? cells.get(idx) : "";
    }

    private static int indexOf(List<String> header, String name) {
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    /** {@code null} when the cell says neither: a conditional phrase is not a claim we can score. */
    private static Boolean parseRequired(String cell) {
        String s = cell.toLowerCase(Locale.ROOT).replace("*", "").replace("`", "").strip();
        if (s.equals("yes")) return Boolean.TRUE;
        if (s.equals("no")) return Boolean.FALSE;
        return null;
    }
}
