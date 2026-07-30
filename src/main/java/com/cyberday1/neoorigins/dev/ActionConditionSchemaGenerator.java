package com.cyberday1.neoorigins.dev;

import com.cyberday1.neoorigins.compat.action.BuiltinActions;
import com.cyberday1.neoorigins.compat.action.BuiltinItemActions;
import com.cyberday1.neoorigins.compat.condition.BuiltinBlockConditions;
import com.cyberday1.neoorigins.compat.condition.BuiltinConditions;
import com.cyberday1.neoorigins.compat.condition.BuiltinItemConditions;
import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Headless generator for {@code docs/schema/action.schema.json} and
 * {@code docs/schema/condition.schema.json} from the {@link BuiltinActions} /
 * {@link BuiltinConditions} FieldSpec registries — the action/condition analogue
 * of {@link PowerSchemaGenerator}, closing the drift gap where these two
 * documents were hand-written (and had silently diverged from the registries).
 *
 * <p><b>Editor-metadata only.</b> The action/condition {@code Codec}/parser path
 * never reads these files, so regeneration is behavior-neutral at runtime.
 *
 * <p><b>Branch rule.</b> One descriptor → one {@code oneOf} branch (NOT the
 * hand-written file's incidental merges / duplicates). The branch's {@code type}
 * discriminator lists, in order: the canonical id, the registry aliases, then each
 * {@link #ECOSYSTEM_NAMESPACES} variant of every one of those. Namespace
 * canonicalisation is blanket in all five verb parsers (any {@code <ns>:<verb>}
 * canonicalises to {@code neoorigins:<verb>}) and — unlike legacy POWER types — the
 * verb parsers remap no fields, so those ids are field-identical and share the
 * branch. Listing them is what gives legacy-namespaced verbs real field validation
 * and editor registration instead of the property-less fallback branch. No pack
 * regresses (the web palette filters non-{@code neoorigins:} ids out as noise).
 *
 * <p><b>Root gate.</b> {@code properties.type} carries a {@code pattern}, not an
 * {@code enum}: the accepted namespace set is unbounded (the parsers' generic
 * namespace fallback dispatches {@code medievalorigins:execute_command} fine), while
 * the LEAF must be known or the verb falls through to the unsupported no-op. The
 * pattern is exactly that — optional namespace, then a known leaf — so typos still
 * fail. The form models derive their type universe from the branch ids when a
 * document has no root enum.
 *
 * <p><b>Determinism.</b> The top-level {@code type.pattern} leaf list and the
 * {@code oneOf} branches are sorted; nodes are built by the shared {@link SchemaNodeBuilder};
 * serialisation is fixed ({@code setPrettyPrinting().disableHtmlEscaping()} + one
 * trailing LF). Re-running on the generator's own output is byte-identical.
 *
 * <p><b>Header.</b> The {@code $schema}/{@code $id}/{@code title}/{@code description}
 * and the {@code type} property's {@code description} are preserved verbatim from
 * the current committed file (same approach as the power generator).
 *
 * <p>Invoke via {@code ./gradlew generateActionConditionSchema} (writes both) or
 * {@code --args="action docs/schema/action.schema.json"} for one document.
 */
public final class ActionConditionSchemaGenerator {

    private ActionConditionSchemaGenerator() {}

    public static final String ACTION = "action";
    public static final String CONDITION = "condition";
    public static final String BLOCK_CONDITION = "block_condition";
    public static final String ITEM_CONDITION = "item_condition";
    public static final String ITEM_ACTION = "item_action";

    private static String defaultOutput(String which) {
        return "docs/schema/" + which + ".schema.json";
    }

    /** The five documents this generator owns, in emission order. */
    private static final List<String> ALL =
        List.of(ACTION, CONDITION, BLOCK_CONDITION, ITEM_CONDITION, ITEM_ACTION);

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            for (String which : ALL) writeSchema(which, Path.of(defaultOutput(which)), null);
        } else if (args[0].equals("--out-dir")) {
            // Regenerate all five into another directory, each still reading its
            // verbatim header from the committed file. This is how schemaDriftVerify
            // compares regenerated output against what is committed without
            // overwriting it.
            if (args.length < 2) throw new IOException("--out-dir needs a directory argument");
            Path dir = Path.of(args[1]);
            for (String which : ALL) {
                writeSchema(which, dir.resolve(which + ".schema.json"), Path.of(defaultOutput(which)));
            }
        } else {
            String which = args[0];
            Path out = Path.of(args.length > 1 ? args[1] : defaultOutput(which));
            writeSchema(which, out, null);
        }
    }

    /**
     * Write one document. {@code headerSource} names the file the verbatim header
     * is read from; {@code null} means the output path itself (regenerate in place).
     */
    private static void writeSchema(String which, Path output, Path headerSource) throws IOException {
        String json = buildSchemaJson(which, headerSource != null ? headerSource : output);
        if (output.getParent() != null) Files.createDirectories(output.getParent());
        Files.writeString(output, json, StandardCharsets.UTF_8);
        System.out.println(which + " schema written to " + output.toAbsolutePath().normalize());
    }

    /** One descriptor's view, abstracting over Action/Condition. */
    private record Descriptor(String canonicalId, List<FieldSpec> fields, List<String> aliasIds) {}

    private static List<Descriptor> descriptorsFor(String which) {
        List<Descriptor> out = new ArrayList<>();
        if (ACTION.equals(which)) {
            BuiltinActions.descriptors().forEach((id, t) ->
                out.add(new Descriptor(id.toString(), t.fields(),
                    t.aliases().stream().map(ResourceLocation::toString).toList())));
        } else if (CONDITION.equals(which)) {
            BuiltinConditions.descriptors().forEach((id, t) ->
                out.add(new Descriptor(id.toString(), t.fields(),
                    t.aliases().stream().map(ResourceLocation::toString).toList())));
        } else if (BLOCK_CONDITION.equals(which)) {
            BuiltinBlockConditions.descriptors().forEach((id, t) ->
                out.add(new Descriptor(id.toString(), t.fields(),
                    t.aliases().stream().map(ResourceLocation::toString).toList())));
        } else if (ITEM_CONDITION.equals(which)) {
            BuiltinItemConditions.descriptors().forEach((id, t) ->
                out.add(new Descriptor(id.toString(), t.fields(),
                    t.aliases().stream().map(ResourceLocation::toString).toList())));
        } else if (ITEM_ACTION.equals(which)) {
            BuiltinItemActions.descriptors().forEach((id, t) ->
                out.add(new Descriptor(id.toString(), t.fields(),
                    t.aliases().stream().map(ResourceLocation::toString).toList())));
        } else {
            throw new IllegalArgumentException("Unknown schema document: " + which
                + " (expected 'action', 'condition', 'block_condition', 'item_condition', or 'item_action')");
        }
        return out;
    }

    /**
     * Ecosystem namespaces emitted as explicit per-branch discriminator variants.
     *
     * <p>All five verb parsers canonicalise by <b>leaf</b>: {@code ActionParser} /
     * {@code ConditionParser} / {@code ItemConditionParser} / {@code ItemActionParser}
     * rewrite {@code <anything>:<leaf>} to {@code neoorigins:<leaf>} and dispatch on
     * that, and {@code ConditionParser}'s block-condition path strips the namespace
     * and matches the bare leaf. Crucially the verb parsers pass the <b>unmodified</b>
     * JSON object to the descriptor factory — unlike legacy POWER types, which remap
     * fields — so a legacy-prefixed verb is field-for-field identical to its
     * {@code neoorigins:} twin and can share the branch.
     *
     * <p>The accepted prefix set is unbounded (the generic namespace fallback lets
     * {@code medievalorigins:execute_command} dispatch), so this list is not a
     * whitelist — the root {@code type.pattern} is what admits arbitrary namespaces.
     * These are the prefixes packs in the wild actually use, listed here so those ids
     * get real branch-level FIELD validation and register in the editors (the form
     * models bind a branch to every id in its {@code const}/{@code enum}) rather than
     * falling to the property-less permissive fallback branch.
     */
    private static final List<String> ECOSYSTEM_NAMESPACES =
        List.of("apace", "origins", "apoli", "apugli");

    /** The leaf (namespace-stripped) part of a verb id. */
    private static String leafOf(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    /**
     * The root {@code type.pattern}: an optional resource-location namespace
     * followed by one of the known verb leaves. Leaves are sorted (determinism)
     * and regex-escaped. The namespace class is the ResourceLocation-legal set
     * ({@code [a-z0-9_.-]}), and it is optional because a bare leaf canonicalises
     * to {@code neoorigins:<leaf>} in every parser.
     */
    private static String leafPattern(Set<String> leaves) {
        StringBuilder sb = new StringBuilder("^(?:[a-z0-9_.-]+:)?(?:");
        boolean first = true;
        for (String leaf : leaves) {
            if (!first) sb.append('|');
            first = false;
            for (int i = 0; i < leaf.length(); i++) {
                char c = leaf.charAt(i);
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_') {
                    sb.append(c);
                } else {
                    sb.append('\\').append(c); // escape regex metacharacters (e.g. `.`)
                }
            }
        }
        return sb.append(")$").toString();
    }

    /**
     * Full ordered, de-duplicated id list a branch matches: canonical, registry
     * aliases, then each {@link #ECOSYSTEM_NAMESPACES} variant of every one of those.
     */
    private static List<String> branchTypeIds(Descriptor d) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.add(d.canonicalId());
        ids.addAll(d.aliasIds());
        List<String> bases = new ArrayList<>(ids);
        for (String ns : ECOSYSTEM_NAMESPACES) {
            for (String base : bases) ids.add(ns + ":" + leafOf(base));
        }
        return new ArrayList<>(ids);
    }

    /**
     * Synthesize a minimal header object for a brand-new ref-doc that has no
     * committed file yet (only its {@code $schema}/{@code $id}/{@code title}/
     * {@code description} and {@code properties.type.description} are read back by
     * {@link #buildSchemaJson}). Once written, the file is read verbatim on every
     * later run, so any manual header edits survive — this is bootstrap-only.
     */
    private static JsonObject synthesizeHeader(String which) {
        String title = switch (which) {
            case BLOCK_CONDITION -> "NeoOrigins Block Condition";
            case ITEM_CONDITION -> "NeoOrigins Item Condition";
            case ITEM_ACTION -> "NeoOrigins Item Action";
            default -> "NeoOrigins " + which;
        };
        String desc = switch (which) {
            case BLOCK_CONDITION -> "A nested block condition used by on_block / block / "
                + "in_block / near_block. The `type` field discriminates.";
            case ITEM_CONDITION -> "A nested item condition used by equipped_item / "
                + "modify_inventory. The `type` field discriminates.";
            case ITEM_ACTION -> "A nested item action used by equipped_item_action / "
                + "modify_inventory. The `type` field discriminates.";
            default -> "Generated NeoOrigins " + which + " schema.";
        };
        String typeDesc = switch (which) {
            case BLOCK_CONDITION -> "Block-condition verb (block / in_tag / and / or). "
                + "`apace:` aliases are accepted.";
            case ITEM_CONDITION -> "Item-condition verb (empty / nbt / enchantment / "
                + "ingredient / not / and / or). `apace:` aliases are accepted.";
            case ITEM_ACTION -> "Item-action verb (and / if_else / merge_nbt / consume / "
                + "damage / set_count). `apace:` aliases are accepted.";
            default -> "Fully-qualified " + which + " verb. `apace:` aliases are accepted.";
        };
        JsonObject header = new JsonObject();
        header.addProperty("$schema", "https://json-schema.org/draft/2020-12/schema");
        header.addProperty("$id", "https://neoorigins.example/schema/" + which + ".schema.json");
        header.addProperty("title", title);
        header.addProperty("description", desc);
        JsonObject props = new JsonObject();
        JsonObject typeNode = new JsonObject();
        typeNode.addProperty("description", typeDesc);
        props.add("type", typeNode);
        header.add("properties", props);
        return header;
    }

    /**
     * The typeless item-condition shorthand as a {@code oneOf} branch.
     *
     * <p>{@code ItemConditionParser.parseInner}'s {@code default} arm reads three
     * top-level fields with no {@code type} at all: {@code id}, {@code item} (an
     * undocumented alias for {@code id} — first one wins) and {@code tag}. Apoli
     * accepts the same shorthand, and NeoOrigins ships four {@code jianxian_*}
     * powers that use it.
     *
     * <p>{@code not: {required: [type]}} makes the branch mutually exclusive with
     * every typed branch and with the fallback, so {@code oneOf}'s exactly-one rule
     * holds. The {@code anyOf} is what keeps typo detection: a typeless object with
     * none of the three fields hits the same unsupported-verb no-op an unknown leaf
     * does — {@code CompatWarningCollector.recordItemConditionUnsupported} plus an
     * always-true condition — and the schema rejects that for the same reason it
     * rejects {@code neoorigins:aply_effect}. Extra keys stay permitted, matching
     * the descriptor branches.
     */
    private static JsonObject typelessItemConditionBranch() {
        JsonObject branch = new JsonObject();
        branch.addProperty("$comment",
            "Typeless shorthand \u2014 ItemConditionParser's default arm reads a bare "
            + "id / item / tag with no `type`.");

        JsonObject noType = new JsonObject();
        JsonArray noTypeRequired = new JsonArray();
        noTypeRequired.add("type");
        noType.add("required", noTypeRequired);
        branch.add("not", noType);

        JsonObject props = new JsonObject();
        props.add("id", shorthandField(
            "Item id the stack must be, e.g. minecraft:diamond_sword. Ignored when `item` is also present."));
        props.add("item", shorthandField(
            "Alias for `id`, and preferred over it when both are present."));
        props.add("tag", shorthandField(
            "Item tag the stack must be in, e.g. minecraft:swords (no leading #)."));
        JsonObject inverted = new JsonObject();
        inverted.addProperty("description",
            "When true the whole check is negated. Read by ItemConditionParser.parse "
            + "for every item condition, typed or not.");
        inverted.addProperty("type", "boolean");
        inverted.addProperty("default", false);
        props.add("inverted", inverted);
        branch.add("properties", props);

        JsonArray anyOf = new JsonArray();
        for (String field : List.of("id", "item", "tag")) {
            JsonObject arm = new JsonObject();
            JsonArray required = new JsonArray();
            required.add(field);
            arm.add("required", required);
            anyOf.add(arm);
        }
        branch.add("anyOf", anyOf);
        return branch;
    }

    /** One of the three typeless shorthand fields: a documented resource-location string. */
    private static JsonObject shorthandField(String description) {
        JsonObject node = new JsonObject();
        node.addProperty("description", description);
        node.addProperty("type", "string");
        node.addProperty("pattern", "^(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+$");
        return node;
    }

    /**
     * Build the full schema JSON string for {@code which} ∈ {action, condition, block_condition}.
     * {@code headerSource} supplies the verbatim header + {@code type} description.
     * Public so the parity harness ({@link ActionConditionSchemaCheck}) can parse
     * the generated text without writing a file.
     */
    public static String buildSchemaJson(String which, Path headerSource) throws IOException {
        JsonObject current;
        if (Files.exists(headerSource)) {
            try {
                current = JsonParser.parseString(Files.readString(headerSource, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            } catch (Exception e) {
                throw new IOException("Cannot read current schema at " + headerSource
                    + " (needed for the verbatim header): " + e.getMessage(), e);
            }
        } else {
            // Bootstrap: no committed file yet (new ref-doc family). Synthesize the
            // header so the first generation can write it; subsequent runs read it
            // back verbatim like action/condition.
            current = synthesizeHeader(which);
        }
        JsonObject currentProps = current.getAsJsonObject("properties");
        JsonObject currentType = currentProps.getAsJsonObject("type");

        List<Descriptor> descriptors = descriptorsFor(which);
        descriptors.sort(Comparator.comparing(Descriptor::canonicalId));

        // ── Header verbatim ──────────────────────────────────────────────────
        JsonObject root = new JsonObject();
        root.add("$schema", current.get("$schema"));
        root.add("$id", current.get("$id"));
        root.add("title", current.get("title"));
        root.add("description", current.get("description"));
        root.addProperty("type", "object");
        // `type` is required in four of the five documents. ItemConditionParser is
        // the exception: parseInner reads `json.has("type") ? … : ""` and its default
        // arm accepts three TYPELESS shorthands — a bare {id}, {item} (an
        // undocumented alias for id) or {tag} — which is how NeoOrigins' own
        // jianxian_* powers write `"item_condition": {"tag": "minecraft:swords"}`.
        // Requiring `type` here made the mod's own shipped content fail its own
        // schema. The shorthand is instead gated by its own oneOf branch below, so
        // an object with neither `type` nor a shorthand field still fails.
        if (!ITEM_CONDITION.equals(which)) {
            JsonArray rootRequired = new JsonArray();
            rootRequired.add("type");
            root.add("required", rootRequired);
        }

        // ── properties.type — leaf-based pattern gate ────────────────────────
        //
        // An `enum` here would be WRONG, not merely incomplete. The parsers
        // canonicalise `<anything>:<leaf>` to `neoorigins:<leaf>` before dispatch
        // (and a bare `<leaf>` likewise), so the accepted namespace set is
        // unbounded — `medievalorigins:execute_command` loads today. What the
        // parser actually checks is the LEAF: an unknown leaf falls through to the
        // unsupported-verb no-op. So mirror exactly that: strip an optional
        // namespace, then require a known leaf. Typo detection is preserved —
        // `neoorigins:aply_effect` has no known leaf and fails.
        Set<String> typeEnum = new TreeSet<>();
        for (Descriptor d : descriptors) typeEnum.addAll(branchTypeIds(d));
        Set<String> leaves = new TreeSet<>();
        for (String id : typeEnum) leaves.add(leafOf(id));

        JsonObject properties = new JsonObject();
        JsonObject typeNode = new JsonObject();
        typeNode.addProperty("type", "string");
        typeNode.add("description", currentType.get("description")); // verbatim
        typeNode.addProperty("pattern", leafPattern(leaves));
        properties.add("type", typeNode);
        root.add("properties", properties);

        // ── oneOf — one branch per descriptor, sorted, fallback LAST ─────────
        JsonArray oneOf = new JsonArray();
        Set<String> branchCanonicalIds = new TreeSet<>();
        for (Descriptor d : descriptors) {
            if (!branchCanonicalIds.add(d.canonicalId())) {
                throw new IOException("Duplicate descriptor for id " + d.canonicalId());
            }
            oneOf.add(SchemaNodeBuilder.buildBranch(d.canonicalId(), branchTypeIds(d), d.fields()));
        }

        // Typeless-shorthand branch — item_condition only. Kept ahead of the
        // fallback so the two read in the order they apply. It carries no
        // `properties.type`, which is exactly how both form models recognise a
        // non-authorable branch and skip it, so the editors keep offering only the
        // explicit `type` forms while a hand-authored shorthand still validates.
        if (ITEM_CONDITION.equals(which)) oneOf.add(typelessItemConditionBranch());

        // Fallback branch — any id not matched by a structured branch above.
        JsonObject fallback = new JsonObject();
        fallback.addProperty("$comment",
            "Fallback branch \u2014 any registered type not exhaustively schema'd yet. "
            + "Allows extra fields permissively.");
        JsonObject fbType = new JsonObject();
        JsonObject not = new JsonObject();
        JsonArray notEnum = new JsonArray();
        for (String id : typeEnum) notEnum.add(id); // TreeSet → sorted, covers all branch ids
        not.add("enum", notEnum);
        fbType.add("not", not);
        JsonObject fbProps = new JsonObject();
        fbProps.add("type", fbType);
        fallback.add("properties", fbProps);
        // The fallback describes "a `type` we have not branched", so it must SAY it
        // needs a `type`. Redundant in the four documents whose root requires one,
        // load-bearing in item_condition: without it `{"tag": …}` would satisfy both
        // this branch (properties.type never applies to an absent key) and the
        // typeless-shorthand branch, and `oneOf` demands exactly one match.
        JsonArray fbRequired = new JsonArray();
        fbRequired.add("type");
        fallback.add("required", fbRequired);
        fallback.addProperty("additionalProperties", true);
        oneOf.add(fallback);

        root.add("oneOf", oneOf);

        return new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create()
            .toJson(root) + "\n";
    }
}
