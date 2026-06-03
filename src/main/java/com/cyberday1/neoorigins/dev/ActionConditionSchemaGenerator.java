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
import net.minecraft.resources.Identifier;

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
 * discriminator lists, in order: the canonical id, the registry aliases, then the
 * {@code apace:} namespace variant of each of those. The {@code apace:} prefix is
 * blanket namespace-canonicalisation in {@code ConditionParser}/{@code ActionParser}
 * (any {@code apace:<verb>} canonicalises to {@code neoorigins:<verb>}), so emitting
 * one {@code apace:} variant per id is complete and deterministic — it supersedes
 * the arbitrary subset the hand-written file happened to list, and regresses no
 * pack (the web palette already filters {@code apace:} out as noise).
 *
 * <p><b>Determinism.</b> Top-level {@code type.enum} and the {@code oneOf} branches
 * are sorted by canonical id; nodes are built by the shared {@link SchemaNodeBuilder};
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

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            writeSchema(ACTION, Path.of(defaultOutput(ACTION)));
            writeSchema(CONDITION, Path.of(defaultOutput(CONDITION)));
            writeSchema(BLOCK_CONDITION, Path.of(defaultOutput(BLOCK_CONDITION)));
            writeSchema(ITEM_CONDITION, Path.of(defaultOutput(ITEM_CONDITION)));
            writeSchema(ITEM_ACTION, Path.of(defaultOutput(ITEM_ACTION)));
        } else {
            String which = args[0];
            Path out = Path.of(args.length > 1 ? args[1] : defaultOutput(which));
            writeSchema(which, out);
        }
    }

    private static void writeSchema(String which, Path output) throws IOException {
        String json = buildSchemaJson(which, output);
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
                    t.aliases().stream().map(Identifier::toString).toList())));
        } else if (CONDITION.equals(which)) {
            BuiltinConditions.descriptors().forEach((id, t) ->
                out.add(new Descriptor(id.toString(), t.fields(),
                    t.aliases().stream().map(Identifier::toString).toList())));
        } else if (BLOCK_CONDITION.equals(which)) {
            BuiltinBlockConditions.descriptors().forEach((id, t) ->
                out.add(new Descriptor(id.toString(), t.fields(),
                    t.aliases().stream().map(Identifier::toString).toList())));
        } else if (ITEM_CONDITION.equals(which)) {
            BuiltinItemConditions.descriptors().forEach((id, t) ->
                out.add(new Descriptor(id.toString(), t.fields(),
                    t.aliases().stream().map(Identifier::toString).toList())));
        } else if (ITEM_ACTION.equals(which)) {
            BuiltinItemActions.descriptors().forEach((id, t) ->
                out.add(new Descriptor(id.toString(), t.fields(),
                    t.aliases().stream().map(Identifier::toString).toList())));
        } else {
            throw new IllegalArgumentException("Unknown schema document: " + which
                + " (expected 'action', 'condition', 'block_condition', 'item_condition', or 'item_action')");
        }
        return out;
    }

    /** {@code apace:} namespace variant of a {@code neoorigins:<verb>} id. */
    private static String apaceVariant(String id) {
        int colon = id.indexOf(':');
        return "apace:" + (colon >= 0 ? id.substring(colon + 1) : id);
    }

    /**
     * Full ordered, de-duplicated id list a branch matches: canonical, registry
     * aliases, then the {@code apace:} variant of each (canonical + aliases).
     */
    private static List<String> branchTypeIds(Descriptor d) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.add(d.canonicalId());
        ids.addAll(d.aliasIds());
        ids.add(apaceVariant(d.canonicalId()));
        for (String a : d.aliasIds()) ids.add(apaceVariant(a));
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
        JsonArray rootRequired = new JsonArray();
        rootRequired.add("type");
        root.add("required", rootRequired);

        // ── properties.type — full sorted id union (all branch ids) ──────────
        Set<String> typeEnum = new TreeSet<>();
        for (Descriptor d : descriptors) typeEnum.addAll(branchTypeIds(d));

        JsonObject properties = new JsonObject();
        JsonObject typeNode = new JsonObject();
        typeNode.addProperty("type", "string");
        typeNode.add("description", currentType.get("description")); // verbatim
        JsonArray enumArr = new JsonArray();
        for (String id : typeEnum) enumArr.add(id);
        typeNode.add("enum", enumArr);
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
