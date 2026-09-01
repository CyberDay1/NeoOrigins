package com.cyberday1.neoorigins.dev;

import com.cyberday1.neoorigins.compat.OriginsCompatPowerLoader;
import com.cyberday1.neoorigins.compat.OriginsFormatDetector;
import com.cyberday1.neoorigins.compat.OriginsMultipleExpander;
import com.cyberday1.neoorigins.compat.OriginsPowerTranslator;
import com.cyberday1.neoorigins.power.registry.BuiltinPowers;
import com.cyberday1.neoorigins.power.registry.LegacyPowerTypeAliases;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gate on the authorable power-type surface: the {@code type} enum in
 * {@code docs/schema/power.schema.json} is what all three editors offer as
 * "types you may write", so every entry in it has to be (a) structurally
 * modelled and (b) something that actually runs on the branch being built.
 *
 * <p>Two assertions, both born from real defects the four-lane audit found:
 *
 * <ol>
 *   <li><b>enum ⊆ const-branches.</b> An enum id with no {@code oneOf} branch
 *       falls through to the permissive {@code type.not.enum} fallback, so the
 *       editors validate nothing and render a raw-JSON box. This is currently
 *       true of a known set of ids, held in {@link #ALLOWLIST} — a temporary,
 *       enumerated, SHRINKING list. Removing a line is the goal; adding one is a
 *       failure. A stale line (the id gained a branch, or left the enum) is also
 *       a failure, so the list cannot quietly outlive the gap it records.</li>
 *   <li><b>enum ⊆ registered-and-functional.</b> Every enum id must resolve, by
 *       one of the documented load-time paths, to a type {@code PowerTypes}
 *       actually registers on this branch: directly, through the legacy-alias
 *       remap, through the compat import translation, through one of the two
 *       compat dispatch switches, or as a container type that is expanded away
 *       before registration. Plus the two reverse directions: every registered
 *       type must be authorable (in the enum), and every container id the
 *       expander accepts must be authorable. This is what catches a type that is
 *       declared and offered to authors but silently does nothing.</li>
 *   <li><b>declared dispatch sets ≡ the switch labels.</b> The legacy half of the
 *       enum comes from {@code OriginsPowerTranslator.ROUTE_A_TYPES} and
 *       {@code OriginsCompatPowerLoader.ROUTE_B_TYPES} /
 *       {@code CONDITIONED_ROUTE_B_TYPES}, which are hand-maintained transcriptions
 *       of the two dispatch switches. This gate parses the {@code case} labels back
 *       out of both source files and diffs BOTH directions, so the schema cannot
 *       advertise a type the parser dropped (the pack validates, then logs "Unknown
 *       power type") and cannot omit one it accepts. It is the hop that makes the
 *       whole legacy surface non-tautological: the enum is built from the sets, and
 *       the sets are pinned to the parser.</li>
 * </ol>
 *
 * <p>The dev harness used to keep a third, silently-stale transcription of the
 * Route B switch — 13 ids behind — which is why this is gated rather than trusted.
 *
 * <p>Run: {@code ./gradlew powerEnumCheck}. Exits non-zero on any finding.
 */
public final class PowerEnumCheck {

    private PowerEnumCheck() {}

    private static final Path SCHEMA = Path.of("docs/schema/power.schema.json");
    private static final Path ALLOWLIST = Path.of("docs/schema/power-enum-branch-allowlist.txt");
    private static final Path POWER_TYPES_SRC =
        Path.of("src/main/java/com/cyberday1/neoorigins/power/registry/PowerTypes.java");
    private static final Path TRANSLATOR_SRC =
        Path.of("src/main/java/com/cyberday1/neoorigins/compat/OriginsPowerTranslator.java");
    private static final Path COMPAT_LOADER_SRC =
        Path.of("src/main/java/com/cyberday1/neoorigins/compat/OriginsCompatPowerLoader.java");

    /** {@code reg("id", new SomePower())} in PowerTypes — the registration surface. */
    private static final Pattern REG = Pattern.compile("reg\\(\\s*\"([a-z0-9_]+)\"\\s*,");

    /** Floors below which the inputs are too small to be believable (anti-vacuity). */
    private static final int MIN_ENUM = 100;
    private static final int MIN_REGISTERED = 100;

    /**
     * Ratchet ceiling on the LEGACY half of the branch gap — the
     * {@code origins:}/{@code apace:}/{@code apoli:}/{@code apugli:} ids the compat
     * dispatch accepts but that have no structured branch, so the editors fall back
     * to a raw-JSON box for them.
     *
     * <p>These are not allowlist lines, for the same reason the container types are
     * not: the allowlist is an enumerated, per-id record of NATIVE powers missing a
     * FieldSpec list, and burying 400 legacy ids in it would drown that signal and
     * make "the target is zero" meaningless. They are also not a permanent exemption
     * — closing them is real work — so they get a counter with a ceiling instead.
     *
     * <p>The gap exists because structured branches are generated from the
     * {@code BuiltinPowers} FieldSpec registry and the compat power types have no
     * such registry: Route B never produces a native power type at all, so there is
     * nothing to reflect a Config record off. Writing one is a separate, larger
     * piece of work.
     *
     * <p>THIS NUMBER MAY ONLY GO DOWN. Lower it as compat types gain branches; if a
     * change pushes it up, that change added an unmodelled authorable type. The one
     * legitimate exception is the compat dispatch genuinely learning a type it did
     * not accept before: those ids were unauthorable at BOTH gates until then, so
     * counting them is a strictly better position than dropping the power.
     *
     * <p>406 → 410, owner-ruled 2026-08-27, under the exception above.
     * {@code prevent_entity_render} joined
     * Route A, costing four ids. The cost is not always four. {@code origins:} and
     * {@code apace:} are enumerated case by case in the dispatch switches, and
     * {@link com.cyberday1.neoorigins.compat.OriginsFormatDetector#legacyPowerTypeSurface()}
     * then expands each id over {@code APOLI_FAMILY_NS} — which is exactly
     * {@code {apoli, apugli}}, not four namespaces. So a type carries four spellings
     * only when it has an {@code apace:} sibling; three {@code origins:} paths have
     * none and cost two.
     */
    private static final int MAX_UNBRANCHED_LEGACY = 410;

    public static void main(String[] args) throws IOException {
        int failures = 0;

        JsonObject root = JsonParser.parseString(
            Files.readString(SCHEMA, StandardCharsets.UTF_8)).getAsJsonObject();
        Set<String> enumIds = readTypeEnum(root);
        Set<String> branchIds = readBranchIds(root);
        Set<String> registered = readRegisteredIds();

        System.out.println("[power-enum] " + enumIds.size() + " enum ids, "
            + branchIds.size() + " structured branches, "
            + registered.size() + " PowerTypes registrations");

        // An empty (or implausibly small) input set would make every assertion
        // below pass while checking nothing. Fail loudly instead.
        if (enumIds.size() < MIN_ENUM) {
            System.out.println("[power-enum] FAIL  only " + enumIds.size() + " ids in the type enum"
                + " (expected at least " + MIN_ENUM + ") — the enum is missing or truncated,"
                + " so these gates had nothing to check");
            failures++;
        }
        if (branchIds.isEmpty()) {
            System.out.println("[power-enum] FAIL  the schema has 0 structured oneOf branches");
            failures++;
        }
        if (registered.size() < MIN_REGISTERED) {
            System.out.println("[power-enum] FAIL  only " + registered.size()
                + " registrations parsed out of " + POWER_TYPES_SRC + " (expected at least "
                + MIN_REGISTERED + ") — the reg(\"id\", ...) shape changed and this gate"
                + " stopped seeing the registry");
            failures++;
        }
        if (failures > 0) {
            report(failures);
            return;
        }

        failures += auditBranchCoverage(enumIds, branchIds);
        failures += auditRegisteredAndFunctional(enumIds, registered);
        failures += auditDispatchParity();
        report(failures);
    }

    private static void report(int failures) {
        System.out.println();
        if (failures > 0) {
            System.out.println("[power-enum] FAILED: " + failures + " finding(s).");
            System.exit(1);
        }
        System.out.println("[power-enum] OK");
    }

    // ── Assertion 1: enum ⊆ structured branches ─────────────────────────────

    /**
     * Every enum id must have a structured {@code oneOf} branch; the ones that do
     * not are held in the shrinking allowlist. Fails on an id that is missing a
     * branch and NOT allowlisted (a regression) and on an allowlist line that no
     * longer describes a real gap (a stale exception).
     */
    private static int auditBranchCoverage(Set<String> enumIds, Set<String> branchIds) throws IOException {
        Set<String> unbranched = new TreeSet<>(enumIds);
        unbranched.removeAll(branchIds);
        // The container types are a PERMANENT exemption, not an allowlist entry:
        // their properties are arbitrary author-chosen sub-power keys, so there is
        // no branch to write and the permissive fallback is the correct shape.
        // Keeping them out of the allowlist keeps that list honestly zero-bound.
        unbranched.removeAll(OriginsMultipleExpander.MULTIPLE_TYPES);

        // The legacy compat surface is counted under a shrinking ceiling rather than
        // enumerated in the allowlist — see MAX_UNBRANCHED_LEGACY for why.
        Set<String> unbranchedLegacy = new TreeSet<>(unbranched);
        unbranchedLegacy.retainAll(OriginsFormatDetector.legacyPowerTypeSurface());
        unbranched.removeAll(unbranchedLegacy);

        Set<String> allowed = readAllowlist();
        Set<String> unexpected = new TreeSet<>(unbranched);
        unexpected.removeAll(allowed);
        Set<String> stale = new TreeSet<>(allowed);
        stale.removeAll(unbranched);

        int fails = 0;
        if (!unexpected.isEmpty()) {
            System.out.println("[power-enum] FAIL  " + unexpected.size() + " enum id(s) have no"
                + " structured branch and are not allowlisted — they fall through to the"
                + " permissive fallback, so the editors validate nothing for them:");
            for (String id : unexpected) System.out.println("    " + id);
            System.out.println("    → the fix is to author the branch (a FieldSpec list in"
                + " BuiltinPowers, then regenerate), NOT to extend " + ALLOWLIST);
            fails += unexpected.size();
        }
        if (!stale.isEmpty()) {
            System.out.println("[power-enum] FAIL  " + stale.size() + " allowlist line(s) are stale"
                + " (the id now has a branch, or is no longer in the enum) — delete them:");
            for (String id : stale) System.out.println("    " + id);
            fails += stale.size();
        }
        if (unbranchedLegacy.size() > MAX_UNBRANCHED_LEGACY) {
            System.out.println("[power-enum] FAIL  " + unbranchedLegacy.size() + " legacy compat"
                + " enum id(s) have no structured branch, over the ratchet ceiling of "
                + MAX_UNBRANCHED_LEGACY + " — this change made " + (unbranchedLegacy.size()
                - MAX_UNBRANCHED_LEGACY) + " more authorable type(s) unmodelled. Give them"
                + " branches, or (only if the parser genuinely gained types) raise the"
                + " ceiling deliberately in " + PowerEnumCheck.class.getSimpleName());
            fails++;
        }
        System.out.println("[power-enum] branch coverage: " + branchIds.size() + " of "
            + (enumIds.size() - OriginsMultipleExpander.MULTIPLE_TYPES.size())
            + " branchable enum ids modelled; " + unbranched.size()
            + " native ids still fall through to the permissive fallback"
            + " (allowlisted, TARGET IS 0), plus " + unbranchedLegacy.size()
            + " legacy compat ids (ceiling " + MAX_UNBRANCHED_LEGACY + ", MAY ONLY SHRINK)");
        return fails;
    }

    // ── Assertion 3: declared dispatch sets ≡ the switch case labels ─────────

    /**
     * The declared Route A / Route B type sets are hand-maintained transcriptions of
     * two {@code switch} statements, and the schema's {@code type} enum is built from
     * them — so a drifted set is a schema that lies in one direction or the other.
     * Parse the {@code case} labels straight back out of both source files and diff.
     *
     * <p>Deliberately source-text based: the labels are compile-time constants in a
     * switch, invisible to reflection, and re-deriving them any other way would just
     * be a second transcription to drift.
     */
    private static int auditDispatchParity() throws IOException {
        int fails = 0;
        Set<String> declaredB = new TreeSet<>(OriginsCompatPowerLoader.ROUTE_B_TYPES);
        declaredB.addAll(OriginsCompatPowerLoader.CONDITIONED_ROUTE_B_TYPES);
        fails += diffLabels("Route B", declaredB,
            switchCaseLabels(COMPAT_LOADER_SRC,
                "private CompatPower.Config parseRouteB", "default -> null;"),
            "OriginsCompatPowerLoader.ROUTE_B_TYPES / CONDITIONED_ROUTE_B_TYPES");
        fails += diffLabels("Route A", new TreeSet<>(OriginsPowerTranslator.ROUTE_A_TYPES),
            switchCaseLabels(TRANSLATOR_SRC,
                "private static Optional<JsonObject> doTranslate", "default -> {"),
            "OriginsPowerTranslator.ROUTE_A_TYPES");
        if (fails == 0) {
            System.out.println("[power-enum] dispatch parity: " + declaredB.size()
                + " Route B + " + OriginsPowerTranslator.ROUTE_A_TYPES.size()
                + " Route A declared ids match their switch case labels exactly");
        }
        return fails;
    }

    private static int diffLabels(String label, Set<String> declared, Set<String> inSource,
                                  String setName) {
        int fails = 0;
        // Anti-vacuity: a changed switch shape would silently parse to nothing.
        if (inSource.size() < 50) {
            System.out.println("[power-enum] FAIL  parsed only " + inSource.size() + " " + label
                + " case label(s) out of the source (expected 50+) — the switch shape changed"
                + " and this parity gate stopped seeing the dispatch table");
            return 1;
        }
        Set<String> missing = new TreeSet<>(inSource);
        missing.removeAll(declared);
        Set<String> extra = new TreeSet<>(declared);
        extra.removeAll(inSource);
        if (!missing.isEmpty()) {
            System.out.println("[power-enum] FAIL  " + missing.size() + " " + label + " case"
                + " label(s) are not declared in " + setName + " — the parser accepts them but"
                + " the schema rejects them, so packs using them fail validation while loading"
                + " fine:");
            for (String id : missing) System.out.println("    " + id);
            fails += missing.size();
        }
        if (!extra.isEmpty()) {
            System.out.println("[power-enum] FAIL  " + extra.size() + " id(s) in " + setName
                + " have no " + label + " case label — the schema advertises a type the parser"
                + " drops, so the pack validates and then logs \"Unknown power type\" at load:");
            for (String id : extra) System.out.println("    " + id);
            fails += extra.size();
        }
        return fails;
    }

    /**
     * String literals appearing in {@code case ... ->} labels of the switch that
     * starts at {@code startMarker} and ends at {@code endMarker}. Line comments are
     * stripped first so a commented-out id or an example in prose is not counted, and
     * only the label region of each arm is scanned so ids passed as ARGUMENTS (e.g.
     * {@code translateSimple("neoorigins:flight")}) are not mistaken for labels.
     */
    private static Set<String> switchCaseLabels(Path src, String startMarker, String endMarker)
            throws IOException {
        List<String> lines = Files.readAllLines(src, StandardCharsets.UTF_8);
        StringBuilder body = new StringBuilder();
        boolean in = false;
        for (String raw : lines) {
            String line = raw.replaceAll("//.*$", "");
            if (!in) {
                if (line.contains(startMarker)) in = true;
                continue;
            }
            if (line.contains(endMarker)) break;
            body.append(line).append('\n');
        }
        Set<String> ids = new TreeSet<>();
        Matcher arm = Pattern.compile("\\bcase\\s+([^;]*?)->").matcher(body);
        while (arm.find()) {
            Matcher lit = Pattern.compile("\"([^\"]+)\"").matcher(arm.group(1));
            while (lit.find()) ids.add(lit.group(1));
        }
        return ids;
    }

    // ── Assertion 2: enum ⊆ registered-and-functional ───────────────────────

    /**
     * Resolve every enum id to a real {@code PowerTypes} registration, and check
     * both reverse directions. Deliberately resolves THROUGH the alias and import
     * tables rather than re-unioning the same sets the generator built the enum
     * from, which would be tautological: the question is whether an offered id
     * lands on something that runs.
     */
    private static int auditRegisteredAndFunctional(Set<String> enumIds, Set<String> registered)
            throws IOException {
        LegacyPowerTypeAliases.bootstrap();
        Set<String> aliasSources = new TreeSet<>();
        for (ResourceLocation rl : LegacyPowerTypeAliases.aliasedTypeIds()) aliasSources.add(rl.toString());
        String translatorSrc = Files.readString(TRANSLATOR_SRC, StandardCharsets.UTF_8);

        int fails = 0;

        // (a) A descriptor without a registration is a power the editors offer,
        //     the schema models in full, and the game then ignores.
        List<String> undeclared = new ArrayList<>();
        for (String id : BuiltinPowers.ids()) {
            if (!registered.contains(id)) undeclared.add(id);
        }
        if (!undeclared.isEmpty()) {
            System.out.println("[power-enum] FAIL  " + undeclared.size() + " BuiltinPowers"
                + " descriptor(s) have no PowerTypes registration on this branch — the power"
                + " is authorable and documented but never loads:");
            for (String id : undeclared) System.out.println("    " + id);
            fails += undeclared.size();
        }

        // (b) A registration missing from the enum is a type nobody can author in
        //     the editors (and which trips the permissive fallback on validation).
        List<String> unauthorable = new ArrayList<>();
        for (String id : registered) {
            if (!enumIds.contains(id)) unauthorable.add(id);
        }
        if (!unauthorable.isEmpty()) {
            System.out.println("[power-enum] FAIL  " + unauthorable.size() + " registered power"
                + " type(s) are missing from the schema type enum, so the editors reject or"
                + " raw-box them:");
            for (String id : unauthorable) System.out.println("    " + id);
            fails += unauthorable.size();
        }

        // (c) Same, for the container ids the expander accepts: apace:multiple was
        //     honoured at load but absent from the enum.
        List<String> unauthorableContainers = new ArrayList<>();
        for (String id : new TreeSet<>(OriginsMultipleExpander.MULTIPLE_TYPES)) {
            if (!enumIds.contains(id)) unauthorableContainers.add(id);
        }
        if (!unauthorableContainers.isEmpty()) {
            System.out.println("[power-enum] FAIL  " + unauthorableContainers.size()
                + " container type id(s) accepted by OriginsMultipleExpander are missing from"
                + " the schema type enum:");
            for (String id : unauthorableContainers) System.out.println("    " + id);
            fails += unauthorableContainers.size();
        }

        // (d) Every offered id must land on a registration by one of the four
        //     documented paths.
        List<String> unresolved = new ArrayList<>();
        for (String id : enumIds) {
            if (!resolves(id, registered, aliasSources, translatorSrc)) unresolved.add(id);
        }
        if (!unresolved.isEmpty()) {
            System.out.println("[power-enum] FAIL  " + unresolved.size() + " enum id(s) do not"
                + " resolve to a registered power type (direct / legacy alias / compat import /"
                + " container) — authors are offered a type that does nothing:");
            for (String id : unresolved) System.out.println("    " + id);
            fails += unresolved.size();
        }
        if (fails == 0) {
            System.out.println("[power-enum] resolution: all " + enumIds.size()
                + " enum ids reach a registered power type");
        }
        return fails;
    }

    /**
     * Follow an authorable id to a registration. Paths, in the order load applies
     * them: an Apoli-family spelling is canonicalised to {@code origins:}; a container
     * type is expanded away before registration; a legacy alias is remapped by
     * {@code LegacyPowerTypeAliases}; a recognized compat import id is rewritten by
     * {@code OriginsPowerTranslator} (asserted here by requiring the translator source
     * to still mention the id, so a curated import id that loses its translation case
     * is caught); a legacy id is dispatched by one of the two compat switches;
     * anything else must be registered outright. Alias/import hops chain, hence the
     * bounded loop.
     *
     * <p>The compat-dispatch path does NOT end at a {@code PowerTypes} registration:
     * Route B builds a {@code CompatPower.Config} directly and never produces a
     * native type. "Functional" there means "a dispatch arm claims it", which
     * {@link #auditDispatchParity()} pins to the actual switch source — so this is
     * not the enum vouching for itself.
     *
     * <p>The canonical form and the authored form are BOTH live at each hop,
     * mirroring {@code PowerDataManager.resolvePowerType}: dispatch is keyed on
     * the canonical id, but when neither switch claims it the loader restores the
     * authored id and offers it to the alias table. So an Apoli-family id whose
     * {@code origins:} spelling has no dispatch case (apugli:action_on_jump) still
     * resolves — through the alias — while one whose spelling DOES have a case
     * (apoli:edible_item) resolves through that case first, exactly as at load.
     */
    private static boolean resolves(String id, Set<String> registered,
                                    Set<String> aliasSources, String translatorSrc) {
        if (OriginsMultipleExpander.MULTIPLE_TYPES.contains(id)) return true;
        String cur = id;
        for (int hop = 0; hop < 4; hop++) {
            String canonical = canonicalize(cur);
            if (registered.contains(canonical)) return true;
            if (OriginsCompatPowerLoader.ROUTE_B_TYPES.contains(canonical)
                || OriginsCompatPowerLoader.CONDITIONED_ROUTE_B_TYPES.contains(canonical)
                || OriginsPowerTranslator.ROUTE_A_TYPES.contains(canonical)) {
                return true;
            }
            if (aliasSources.contains(cur)) {
                ResourceLocation rl = ResourceLocation.parse(cur);
                cur = LegacyPowerTypeAliases.simulateApply(rl, new JsonObject(), rl).toString();
                continue;
            }
            if (OriginsPowerTranslator.SCHEMA_RECOGNIZED_IMPORT_IDS.contains(canonical)) {
                if (!translatorSrc.contains("\"" + canonical + "\"")) return false;
                cur = "neoorigins:" + ResourceLocation.parse(canonical).getPath();
                continue;
            }
            return false;
        }
        return false;
    }

    /**
     * Mirror of {@code OriginsFormatDetector.canonicalizePowerType} for a bare id
     * (that method takes the JsonObject it mutates). Kept in step by the same parity
     * gate that pins the dispatch sets: an Apoli-family id only reaches the enum by
     * being derived from an {@code origins:} dispatch label.
     */
    private static String canonicalize(String id) {
        int colon = id.indexOf(':');
        if (colon <= 0) return id;
        String ns = id.substring(0, colon);
        return ("apoli".equals(ns) || "apugli".equals(ns))
            ? "origins:" + id.substring(colon + 1)
            : id;
    }

    // ── Inputs ──────────────────────────────────────────────────────────────

    private static Set<String> readTypeEnum(JsonObject root) {
        Set<String> ids = new TreeSet<>();
        JsonObject props = root.getAsJsonObject("properties");
        if (props == null || !props.has("type")) return ids;
        JsonObject type = props.getAsJsonObject("type");
        if (!type.has("enum")) return ids;
        for (JsonElement e : type.getAsJsonArray("enum")) ids.add(e.getAsString());
        return ids;
    }

    /** The ids that have a structured branch: {@code properties.type.const} (or a multi-id enum). */
    private static Set<String> readBranchIds(JsonObject root) {
        Set<String> ids = new TreeSet<>();
        if (!root.has("oneOf")) return ids;
        for (JsonElement be : root.getAsJsonArray("oneOf")) {
            if (!be.isJsonObject()) continue;
            JsonObject branch = be.getAsJsonObject();
            if (!branch.has("properties")) continue;
            JsonObject bprops = branch.getAsJsonObject("properties");
            if (!bprops.has("type") || !bprops.get("type").isJsonObject()) continue;
            JsonObject typeNode = bprops.getAsJsonObject("type");
            if (typeNode.has("const")) {
                ids.add(typeNode.get("const").getAsString());
            } else if (typeNode.has("enum")) {
                // The permissive fallback is `type.not.enum`, never `type.enum`,
                // so a branch keyed by enum genuinely models each listed id.
                for (JsonElement e : typeNode.getAsJsonArray("enum")) ids.add(e.getAsString());
            }
        }
        return ids;
    }

    private static Set<String> readRegisteredIds() throws IOException {
        Set<String> ids = new TreeSet<>();
        Matcher m = REG.matcher(Files.readString(POWER_TYPES_SRC, StandardCharsets.UTF_8));
        while (m.find()) ids.add("neoorigins:" + m.group(1));
        return ids;
    }

    private static Set<String> readAllowlist() throws IOException {
        Set<String> ids = new TreeSet<>();
        if (!Files.isRegularFile(ALLOWLIST)) return ids;
        for (String line : Files.readAllLines(ALLOWLIST, StandardCharsets.UTF_8)) {
            String s = line.trim();
            if (s.isEmpty() || s.startsWith("#")) continue;
            ids.add(s);
        }
        return ids;
    }
}
