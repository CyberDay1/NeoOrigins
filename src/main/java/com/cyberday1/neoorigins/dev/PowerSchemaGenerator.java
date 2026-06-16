package com.cyberday1.neoorigins.dev;

import com.cyberday1.neoorigins.compat.OriginsPowerTranslator;
import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.power.registry.BuiltinPowers;
import com.cyberday1.neoorigins.power.registry.LegacyPowerTypeAliases;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Headless generator for {@code docs/schema/power.schema.json} (Phase 2 / D3).
 *
 * <p>Emits a full per-power JSON-Schema from the {@link BuiltinPowers} FieldSpec
 * registry, replacing the thin hand-written file with one structured {@code oneOf}
 * branch per registered power. This is <b>editor-metadata only</b>: the power
 * {@code Codec} parse path never reads this file, so regeneration is
 * behavior-neutral at runtime.
 *
 * <p>Determinism contract (D3): both the top-level {@code type.enum} and the
 * per-power {@code oneOf} branches are sorted alphabetically by id (NOT
 * registration order), the whole tree is built with insertion-ordered
 * {@link JsonObject}/{@link JsonArray}, and serialization is fixed
 * ({@code setPrettyPrinting().disableHtmlEscaping()} + a single trailing LF).
 * Re-running the generator on its own output must be byte-identical.
 *
 * <p>The two unrepresentable branches ({@code neoorigins:particle},
 * {@code neoorigins:starting_equipment}) are parsed out of the CURRENT committed
 * file as {@link JsonObject}s and spliced into the generated {@code oneOf} at
 * their sorted position, so they re-serialize through the SAME pretty-printer
 * (uniform formatting, byte-stable). Their field shapes are never hand-written
 * or regenerated here.
 *
 * <p>Invoke via {@code ./gradlew generatePowerSchema} or:
 * <pre>./gradlew generatePowerSchema --args="docs/schema/power.schema.json"</pre>
 */
public final class PowerSchemaGenerator {

    private PowerSchemaGenerator() {}

    private static final String DEFAULT_OUTPUT = "docs/schema/power.schema.json";

    /** The ids that exist in the enum by design but have no in-code descriptor. */
    private static final String ID_PARTICLE = "neoorigins:particle";
    private static final String ID_STARTING_EQUIPMENT = "neoorigins:starting_equipment";
    /**
     * The native sub-power container. It is expanded away at datapack load
     * (see {@link com.cyberday1.neoorigins.compat.OriginsMultipleExpander}) and
     * never registers a runtime descriptor, so it has no structured branch — it
     * lives in the enum and matches the permissive fallback, which is the correct
     * shape since its sub-power keys are arbitrary.
     */
    private static final String ID_MULTIPLE = "neoorigins:multiple";

    public static void main(String[] args) throws IOException {
        Path output = Path.of(args.length > 0 ? args[0] : DEFAULT_OUTPUT);

        // The current committed file is the source for (a) the verbatim header,
        // (b) the verbatim `type` description + the three common-field fragments,
        // and (c) the two preserved branch objects (particle / starting_equipment).
        JsonObject current;
        try {
            current = JsonParser.parseString(Files.readString(output, StandardCharsets.UTF_8))
                .getAsJsonObject();
        } catch (Exception e) {
            throw new IOException("Cannot read current schema at " + output
                + " (needed for header + preserved branches): " + e.getMessage(), e);
        }
        JsonObject currentProps = current.getAsJsonObject("properties");
        JsonObject currentType = currentProps.getAsJsonObject("type");

        // ── 1. Header verbatim ───────────────────────────────────────────────
        JsonObject root = new JsonObject();
        root.add("$schema", current.get("$schema"));
        root.add("$id", current.get("$id"));
        root.add("title", current.get("title"));
        root.add("description", current.get("description"));
        root.addProperty("type", "object");
        JsonArray rootRequired = new JsonArray();
        rootRequired.add("type");
        root.add("required", rootRequired);

        // ── 2. properties ────────────────────────────────────────────────────
        JsonObject properties = new JsonObject();

        // type: full sorted id list.
        List<String> typeEnum = buildTypeEnum();
        JsonObject typeNode = new JsonObject();
        typeNode.addProperty("type", "string");
        typeNode.add("description", currentType.get("description")); // verbatim
        JsonArray enumArr = new JsonArray();
        for (String id : typeEnum) enumArr.add(id);
        typeNode.add("enum", enumArr);
        properties.add("type", typeNode);

        // name / description / hidden — the three common-field fragments VERBATIM.
        properties.add("name", currentProps.get("name"));
        properties.add("description", currentProps.get("description"));
        properties.add("hidden", currentProps.get("hidden"));

        // required_mods — universal soft-compat gate read by the datapack loader
        // for EVERY power type, so it is emitted as a fourth common property.
        // Constructed in code (not copied from the current file) because it used
        // to be a hand edit that every regen dropped.
        JsonObject requiredMods = new JsonObject();
        requiredMods.addProperty("description",
            "Array of mod ids that must ALL be loaded for this power to load at all. "
            + "If any listed mod is absent, the power is skipped at datapack load \u2014 it "
            + "never registers, syncs, or appears in the origin panel. Use for soft-compat "
            + "powers that drive another mod (e.g. [\"dragonsurvival\"]).");
        requiredMods.addProperty("type", "array");
        JsonObject requiredModsItems = new JsonObject();
        requiredModsItems.addProperty("type", "string");
        requiredMods.add("items", requiredModsItems);
        properties.add("required_mods", requiredMods);

        root.add("properties", properties);

        // ── 3. oneOf — one branch per registered power, sorted by id, with the
        //       2 preserved branches spliced in and the fallback LAST. ─────────
        // Collect (id -> branch JsonObject) for every structured branch, keyed
        // so we can sort the whole set by id together.
        record Branch(String id, JsonObject node) {}
        List<Branch> branches = new ArrayList<>();

        // Generated branches from BuiltinPowers descriptors.
        for (var entry : BuiltinPowers.descriptors().entrySet()) {
            String id = entry.getKey().toString();
            branches.add(new Branch(id, buildPowerBranch(id, entry.getValue().fields())));
        }

        // Preserved branches parsed out of the current committed file. These are
        // hand-written, unrepresentable shapes spliced back verbatim. Whether a
        // given branch exists is per-branch: 1.21.1 ships both particle AND a
        // structured starting_equipment branch, while 26.1 only has particle
        // (starting_equipment lives in the enum and falls to the fallback). So
        // splice a preserved branch ONLY if the source file actually has one;
        // otherwise its id stays in the enum (added in buildTypeEnum) and matches
        // the permissive fallback. This keeps regeneration behavior-neutral on
        // either branch instead of fabricating a branch that wasn't there.
        for (String id : List.of(ID_PARTICLE, ID_STARTING_EQUIPMENT)) {
            JsonObject preserved = findPreservedBranch(current, id);
            if (preserved != null) {
                branches.add(new Branch(id, preserved));
            }
        }

        // Sort ALL branches together, alphabetically by id.
        branches.sort(Comparator.comparing(Branch::id));

        // The set of branch ids that now have a structured branch — used both for
        // the fallback `not.enum` and the fail-fast assertion.
        Set<String> branchIds = new TreeSet<>();
        for (Branch b : branches) {
            if (!branchIds.add(b.id())) {
                throw new IOException("Duplicate structured branch for id " + b.id());
            }
        }

        JsonArray oneOf = new JsonArray();
        for (Branch b : branches) oneOf.add(b.node());

        // Fallback branch — always LAST, not sorted.
        JsonObject fallback = new JsonObject();
        fallback.addProperty("$comment",
            "Fallback branch \u2014 any registered type not exhaustively schema'd yet. "
            + "Allows extra fields permissively.");
        JsonObject fbType = new JsonObject();
        JsonObject not = new JsonObject();
        JsonArray notEnum = new JsonArray();
        for (String id : branchIds) notEnum.add(id); // TreeSet → sorted
        not.add("enum", notEnum);
        fbType.add("not", not);
        JsonObject fbProps = new JsonObject();
        fbProps.add("type", fbType);
        fallback.add("properties", fbProps);
        fallback.addProperty("additionalProperties", true);
        oneOf.add(fallback);

        // Fail-fast: the fallback's not.enum must EXACTLY equal the set of emitted
        // branch ids (minus the fallback itself).
        Set<String> notEnumSet = new TreeSet<>();
        for (JsonElement e : notEnum) notEnumSet.add(e.getAsString());
        if (!notEnumSet.equals(branchIds)) {
            throw new IOException("Fallback not.enum diverged from emitted branch ids.\n"
                + "  branchIds=" + branchIds + "\n  notEnum=" + notEnumSet);
        }

        root.add("oneOf", oneOf);

        // ── 4. Serialize ─────────────────────────────────────────────────────
        String json = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create()
            .toJson(root) + "\n";

        if (output.getParent() != null) Files.createDirectories(output.getParent());
        Files.writeString(output, json, StandardCharsets.UTF_8);

        System.out.println("power schema written to " + output.toAbsolutePath().normalize());
        System.out.println("  type.enum ids: " + typeEnum.size());
        System.out.println("  structured branches (incl. preserved, excl. fallback): "
            + branchIds.size());
    }

    /**
     * Assemble the sorted, de-duplicated full id list for {@code type.enum}:
     * {@code BuiltinPowers.ids()} ∪ the 2 unregistered-by-design ids ∪
     * {@link LegacyPowerTypeAliases#aliasedTypeIds()} ∪
     * {@link OriginsPowerTranslator#SCHEMA_RECOGNIZED_IMPORT_IDS}.
     *
     * <p>Every id is enumerated by some in-code table — there are no hard-coded
     * ids here. The cross-mod {@code apace:*} import ids come from the compat
     * layer (the translator), which owns that surface; {@code apoli:*}/
     * {@code apugli:*} come from the alias table; everything else from
     * {@code BuiltinPowers}.
     */
    private static List<String> buildTypeEnum() {
        // The alias table is lazy — bootstrap it before reading aliasedTypeIds().
        LegacyPowerTypeAliases.bootstrap();

        Set<String> ids = new TreeSet<>(BuiltinPowers.ids());
        ids.add(ID_PARTICLE);
        ids.add(ID_STARTING_EQUIPMENT);
        ids.add(ID_MULTIPLE);
        for (Identifier rl : LegacyPowerTypeAliases.aliasedTypeIds()) {
            ids.add(rl.toString());
        }
        ids.addAll(OriginsPowerTranslator.SCHEMA_RECOGNIZED_IMPORT_IDS);
        return new ArrayList<>(ids); // TreeSet → alphabetical
    }

    /**
     * Build one structured {@code oneOf} branch for a registered power.
     *
     * <p>Shape: {@code {"$comment":"<id>","properties":{"type":{"const":"<id>"},
     * <field>:<node>...},"required":["type", <required field names>...]}}.
     * Marker-only powers (empty field list) emit just
     * {@code properties:{"type":{"const":id}}} with {@code required:["type"]}.
     */
    private static JsonObject buildPowerBranch(String id, List<FieldSpec> fields) {
        // A power matches a single id → const discriminator (List.of(id)). Field
        // nodes are emitted by the shared SchemaNodeBuilder (single source for all
        // three documents), so this stays byte-identical to the prior inline impl.
        return SchemaNodeBuilder.buildBranch(id, List.of(id), fields);
    }

    /**
     * Find the preserved branch object for {@code id} in the current committed
     * schema's {@code oneOf}, matched by either {@code $comment} prefix or
     * {@code properties.type.const}.
     */
    private static JsonObject findPreservedBranch(JsonObject current, String id) {
        JsonArray oneOf = current.getAsJsonArray("oneOf");
        if (oneOf == null) return null;
        for (JsonElement el : oneOf) {
            if (!el.isJsonObject()) continue;
            JsonObject branch = el.getAsJsonObject();
            // Prefer the const match (authoritative); fall back to $comment prefix.
            JsonObject props = branch.getAsJsonObject("properties");
            if (props != null && props.has("type") && props.get("type").isJsonObject()) {
                JsonObject t = props.getAsJsonObject("type");
                if (t.has("const") && id.equals(t.get("const").getAsString())) {
                    return branch;
                }
            }
            if (branch.has("$comment")) {
                String c = branch.get("$comment").getAsString();
                if (c.equals(id) || c.startsWith(id + " ") || c.startsWith(id + "\u2014")) {
                    return branch;
                }
            }
        }
        return null;
    }
}
