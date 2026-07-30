package com.cyberday1.neoorigins.dev;

import com.cyberday1.neoorigins.compat.OriginsMultipleExpander;
import com.cyberday1.neoorigins.compat.OriginsPowerTranslator;
import com.cyberday1.neoorigins.power.registry.BuiltinPowers;
import com.cyberday1.neoorigins.power.registry.LegacyPowerTypeAliases;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;

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
 *   <li><b>enum &sube; const-branches.</b> An enum id with no {@code oneOf} branch
 *       falls through to the permissive {@code type.not.enum} fallback, so the
 *       editors validate nothing and render a raw-JSON box. This is currently
 *       true of a known set of ids, held in {@link #ALLOWLIST} — a temporary,
 *       enumerated, SHRINKING list. Removing a line is the goal; adding one is a
 *       failure. A stale line (the id gained a branch, or left the enum) is also
 *       a failure, so the list cannot quietly outlive the gap it records.</li>
 *   <li><b>enum &sube; registered-and-functional.</b> Every enum id must resolve, by
 *       one of the documented load-time paths, to a type {@code PowerTypes}
 *       actually registers on this branch: directly, through the legacy-alias
 *       remap, through the compat import translation, or as a container type that
 *       is expanded away before registration. Plus the two reverse directions:
 *       every registered type must be authorable (in the enum), and every
 *       container id the expander accepts must be authorable. This is what catches
 *       a type that is declared and offered to authors but silently does
 *       nothing.</li>
 * </ol>
 *
 * <p><b>Not asserted on this branch:</b> the lead branch additionally diffs the
 * declared Route A / Route B dispatch sets against the {@code case} labels of the
 * two compat switches, because there the schema {@code type} enum is BUILT from
 * those hand-maintained sets and a drifted set is a schema that lies. On 26.x the
 * enum is assembled from {@code BuiltinPowers.ids()}, the alias table and
 * {@code SCHEMA_RECOGNIZED_IMPORT_IDS} only — the legacy dispatch surface is not
 * advertised to authors at all, {@code OriginsPowerTranslator} has no
 * {@code ROUTE_A_TYPES}, and {@code OriginsCompatPowerLoader.ROUTE_B_TYPES} is
 * private with no second transcription to drift against. There is no subject for
 * that assertion here, so it is omitted rather than faked; if a dispatch-only id
 * ever does reach the enum, assertion 2's resolution sweep fails on it.
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

    /** {@code reg("id", new SomePower())} in PowerTypes — the registration surface. */
    private static final Pattern REG = Pattern.compile("reg\\(\\s*\"([a-z0-9_]+)\"\\s*,");

    /** Floors below which the inputs are too small to be believable (anti-vacuity). */
    private static final int MIN_ENUM = 100;
    private static final int MIN_REGISTERED = 100;

    /**
     * Ratchet ceiling on the LEGACY half of the branch gap — the
     * {@code origins:}/{@code apace:}/{@code apoli:}/{@code apugli:} ids the alias
     * table and the compat import translation accept but that have no structured
     * branch, so the editors fall back to a raw-JSON box for them.
     *
     * <p>These are not allowlist lines, for the same reason the container types are
     * not: the allowlist is an enumerated, per-id record of NATIVE powers missing a
     * FieldSpec list, and burying the imported spellings in it would drown that
     * signal and make "the target is zero" meaningless. They are also not a
     * permanent exemption — folding an import id into its translation target's
     * branch is real work — so they get a counter with a ceiling instead.
     *
     * <p>THIS NUMBER MAY ONLY GO DOWN. Lower it as legacy spellings gain branches;
     * if a change pushes it up, that change added an unmodelled authorable type.
     * It is measured per branch: the registries differ between 1.21.1 and 26.x, so
     * this value is NOT comparable to the lead branch's.
     */
    private static final int MAX_UNBRANCHED_LEGACY = 32;

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
    private static int auditBranchCoverage(Set<String> enumIds, Set<String> branchIds)
            throws IOException {
        Set<String> unbranched = new TreeSet<>(enumIds);
        unbranched.removeAll(branchIds);
        // The container types are a PERMANENT exemption, not an allowlist entry:
        // their properties are arbitrary author-chosen sub-power keys, so there is
        // no branch to write and the permissive fallback is the correct shape.
        // Keeping them out of the allowlist keeps that list honestly zero-bound.
        unbranched.removeAll(OriginsMultipleExpander.MULTIPLE_TYPES);

        // The legacy import surface is counted under a shrinking ceiling rather than
        // enumerated in the allowlist — see MAX_UNBRANCHED_LEGACY for why.
        Set<String> unbranchedLegacy = new TreeSet<>(unbranched);
        unbranchedLegacy.retainAll(legacyImportSurface());
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
                + " branches, or (only if the import surface genuinely grew) raise the"
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

    /**
     * The non-native spellings the enum can legitimately carry on this branch:
     * the {@code apoli:}/{@code apugli:}/{@code origins:} ids the alias table
     * remaps and the cross-mod import ids the translator rewrites. Read from the
     * live tables, not transcribed — these are the same two sources
     * {@code PowerSchemaGenerator.buildTypeEnum()} unions in.
     */
    private static Set<String> legacyImportSurface() {
        LegacyPowerTypeAliases.bootstrap();
        Set<String> ids = new TreeSet<>();
        for (Identifier rl : LegacyPowerTypeAliases.aliasedTypeIds()) ids.add(rl.toString());
        ids.addAll(OriginsPowerTranslator.SCHEMA_RECOGNIZED_IMPORT_IDS);
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
        for (Identifier rl : LegacyPowerTypeAliases.aliasedTypeIds()) aliasSources.add(rl.toString());
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

        // (d) Every offered id must land on a registration by one of the
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
     * is caught); anything else must be registered outright. Alias/import hops chain,
     * hence the bounded loop.
     */
    private static boolean resolves(String id, Set<String> registered, Set<String> aliasSources,
                                    String translatorSrc) {
        if (OriginsMultipleExpander.MULTIPLE_TYPES.contains(id)) return true;
        String cur = canonicalize(id);
        for (int hop = 0; hop < 4; hop++) {
            if (registered.contains(cur)) return true;
            if (aliasSources.contains(cur)) {
                Identifier rl = Identifier.parse(cur);
                cur = LegacyPowerTypeAliases.simulateApply(rl, new JsonObject(), rl).toString();
                continue;
            }
            if (OriginsPowerTranslator.SCHEMA_RECOGNIZED_IMPORT_IDS.contains(cur)) {
                if (!translatorSrc.contains("\"" + cur + "\"")) return false;
                cur = "neoorigins:" + Identifier.parse(cur).getPath();
                continue;
            }
            return false;
        }
        return false;
    }

    /**
     * Mirror of {@code OriginsFormatDetector.canonicalizePowerType} for a bare id
     * (that method takes the JsonObject it mutates).
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
