package com.cyberday1.neoorigins.dev;

import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.power.registry.BuiltinPowers;
import com.cyberday1.neoorigins.power.registry.LegacyAliasPowerSpecs;
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

    /** Stand-in for {@code \|} so an escaped pipe does not split a cell. */
    private static final String PIPE_ESCAPE = "\u0000PIPE\u0000";

    /** One power's documented surface: its first field table, plus the marker-only claim. */
    private record DocSection(String id, int line, Map<String, DocRow> rows,
                              boolean claimsNoAdditional, boolean hasTable, int skippedRows) {}

    /** One row of a doc field table, columns resolved by header name. */
    private record DocRow(String field, Boolean required, String defaultValue, int line) {}

    public static void main(String[] args) throws IOException {
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

        // ---- coverage -------------------------------------------------------
        Set<String> anchored = headingIds(lines);
        Set<String> docOnlyIds = new TreeSet<>(docs.keySet());
        docOnlyIds.removeAll(registry.keySet());

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
                continue; // the whole section is the defect; do not also list every field
            }
            if (!sec.hasTable() && !sec.claimsNoAdditional() && !specs.isEmpty()) {
                untabled.add(id + "  (doc line " + sec.line() + " has no field table; registry declares "
                    + specs.size() + ")");
                continue;
            }

            for (Map.Entry<String, FieldSpec> e : byName.entrySet()) {
                if (UNIVERSAL.contains(e.getKey())) continue;
                DocRow row = sec.rows().get(e.getKey());
                if (row == null) {
                    registryOnly.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(id);
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

        System.out.println();
        System.out.println("[doc-fields] " + defects + " findings (report only; this task never fails the build)");
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
