package com.cyberday1.neoorigins.power.schemaform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Parses {@code power.schema.json} and resolves, per power type, the list of
 * {@link FormFieldSpec}s the in-game creator renders for powers that have a
 * structured schema branch. Powers without a branch fall back to
 * {@link CodecFieldSpecExtractor} (Config-record reflection).
 *
 * <p>Schema shape (draft 2020-12): a common {@code properties} block
 * (type/name/description/hidden) shared by every power, plus a {@code oneOf}
 * array of branches. A <em>structured</em> branch carries
 * {@code "$comment": "<powerId>"}, a {@code properties} map of that power's
 * fields, and {@code required}. A final <em>fallback</em> branch matches every
 * type NOT in its {@code type.not.enum} list with {@code additionalProperties:
 * true} — i.e. no field info at all.
 *
 * <p>At runtime the schema is loaded from the classpath resource
 * {@code /data/neoorigins/schema/power.schema.json} (the build copies it there
 * from {@code docs/schema/}); {@link #load(Path)} remains for headless tooling.
 */
public final class SchemaFormModel {

    /** Classpath location the build's processResources copy writes the schema to. */
    public static final String RESOURCE_PATH = "/data/neoorigins/schema/power.schema.json";

    /** Sibling classpath resources for the DSL action / condition schemas. */
    public static final String ACTION_RESOURCE_PATH    = "/data/neoorigins/schema/action.schema.json";
    public static final String CONDITION_RESOURCE_PATH = "/data/neoorigins/schema/condition.schema.json";
    /** Reusable ref-doc sub-shapes (Step 3): nested block_condition picker. */
    public static final String BLOCK_CONDITION_RESOURCE_PATH = "/data/neoorigins/schema/block_condition.schema.json";
    /** Reusable ref-doc sub-shapes (Step 3): nested item_condition picker. */
    public static final String ITEM_CONDITION_RESOURCE_PATH = "/data/neoorigins/schema/item_condition.schema.json";
    /** Reusable ref-doc sub-shapes (Step 3): nested item_action picker. */
    public static final String ITEM_ACTION_RESOURCE_PATH = "/data/neoorigins/schema/item_action.schema.json";

    /** Common fields every power shares (from root {@code properties}). */
    private final List<FormFieldSpec> commonFields = new ArrayList<>();
    /** powerId → its structured field list (common + branch), if it has a branch. */
    private final Map<String, List<FormFieldSpec>> structured = new LinkedHashMap<>();
    /**
     * The type universe. Read from the root {@code type.enum} when the document
     * has one ({@code power.schema.json}); when the root gates {@code type} with
     * a {@code pattern} instead — the verb ref-docs do, because the parser's
     * namespace canonicalisation accepts ANY prefix, so no enum could be
     * complete — it is the union of the structured branches' own
     * {@code const}/{@code enum} ids, which is what the enum was derived from.
     */
    private final Set<String> allTypes = new TreeSet<>();

    private SchemaFormModel() {}

    /** Load from a filesystem path (headless tooling / tests). */
    public static SchemaFormModel load(Path schemaFile) throws IOException {
        return parseJson(Files.readString(schemaFile));
    }

    /** Load from an open stream (runtime classpath resource); does not close it. */
    public static SchemaFormModel load(InputStream in) throws IOException {
        return parseJson(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }

    /**
     * Load from the packaged classpath resource ({@link #RESOURCE_PATH}). Throws
     * {@link UncheckedIOException} if the resource is missing — that means the
     * build's processResources copy step did not run, a hard packaging error.
     */
    public static SchemaFormModel loadFromClasspath() {
        return loadFromClasspath(RESOURCE_PATH);
    }

    /**
     * Load any packaged schema resource by classpath path. Used by the action /
     * condition variants in addition to the default power schema.
     */
    public static SchemaFormModel loadFromClasspath(String resourcePath) {
        try (InputStream in = SchemaFormModel.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new UncheckedIOException(new IOException(
                    "schema not on classpath at " + resourcePath
                        + " — build processResources copy missing"));
            }
            return load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static SchemaFormModel parseJson(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        SchemaFormModel m = new SchemaFormModel();
        m.parse(root);
        return m;
    }

    private void parse(JsonObject root) {
        JsonObject props = root.getAsJsonObject("properties");
        Set<String> rootRequired = readRequired(root);

        // Universe of types from properties.type.enum, when there is one. A
        // pattern-gated root (the verb ref-docs) carries no enum; the branch walk
        // below then contributes the universe instead.
        JsonObject rootType = props.getAsJsonObject("type");
        if (rootType != null && rootType.has("enum") && rootType.get("enum").isJsonArray()) {
            for (JsonElement e : rootType.getAsJsonArray("enum")) allTypes.add(e.getAsString());
        }

        // Common fields: every root property except the `type` discriminator.
        for (Map.Entry<String, JsonElement> e : props.entrySet()) {
            if (e.getKey().equals("type")) continue;
            commonFields.add(mapProperty(e.getKey(), e.getValue().getAsJsonObject(),
                rootRequired.contains(e.getKey())));
        }

        // Structured oneOf branches. `power.schema.json` uses {"type": {"const": "<id>"}};
        // `action.schema.json` / `condition.schema.json` use {"type": {"enum": [<ids…>]}}
        // where the same branch can apply to multiple ids (e.g. a "neoorigins:foo"
        // entry alongside its "apace:foo" alias). Register the branch against
        // every id listed.
        if (!root.has("oneOf")) return;
        for (JsonElement be : root.getAsJsonArray("oneOf")) {
            JsonObject branch = be.getAsJsonObject();
            JsonObject bprops = branch.has("properties") ? branch.getAsJsonObject("properties") : null;
            if (bprops == null || !bprops.has("type")) continue;
            JsonObject typeProp = bprops.getAsJsonObject("type");

            List<String> branchIds = new ArrayList<>(2);
            if (typeProp.has("const")) {
                branchIds.add(typeProp.get("const").getAsString());
            } else if (typeProp.has("enum")) {
                JsonArray ids = typeProp.getAsJsonArray("enum");
                Set<String> seen = new TreeSet<>();
                for (JsonElement idEl : ids) {
                    if (!idEl.isJsonPrimitive()) continue;
                    String id = idEl.getAsString();
                    if (seen.add(id)) branchIds.add(id);
                }
            }
            if (branchIds.isEmpty()) continue; // fallback branch uses type.not.enum — skip

            Set<String> req = readRequired(branch);
            List<FormFieldSpec> fields = new ArrayList<>(commonFields);
            for (Map.Entry<String, JsonElement> e : bprops.entrySet()) {
                if (e.getKey().equals("type")) continue;
                fields.add(mapProperty(e.getKey(), e.getValue().getAsJsonObject(),
                    req.contains(e.getKey())));
            }
            for (String id : branchIds) {
                structured.put(id, fields);
                // Pattern-gated roots have no enum to seed the universe from; the
                // branch ids ARE the universe there. Adding unconditionally is a
                // no-op for enum-gated documents, whose enum is the union of
                // exactly these ids.
                allTypes.add(id);
            }
        }
    }

    private static Set<String> readRequired(JsonObject o) {
        Set<String> s = new TreeSet<>();
        if (o.has("required")) for (JsonElement e : o.getAsJsonArray("required")) s.add(e.getAsString());
        return s;
    }

    /** Map a single JSON-schema property node to a {@link FormFieldSpec}. */
    private static FormFieldSpec mapProperty(String name, JsonObject p, boolean required) {
        String desc = p.has("description") ? p.get("description").getAsString() : null;
        String ref = p.has("$ref") ? p.get("$ref").getAsString() : null;
        Object def = p.has("default") ? unwrap(p.get("default")) : null;

        // Scalar-or-array $ref: {"oneOf":[{"$ref":X},{"type":"array","items":{"$ref":X}}]}
        // — the "one action/condition, or a list of them" idiom the power schema
        // emits for every native REF field (SchemaNodeBuilder's
        // widenActionConditionRefs). Without this, `ref` stayed null (it is only
        // ever read off a TOP-LEVEL $ref), the oneOf branch below degraded the
        // field to MIXED, and the in-game walker fell through to a raw-JSON
        // textarea — so the recursive action/condition picker never appeared on
        // ~20 fields across 10 power types. Resolve it to the ARRAY_REF kind
        // (ARRAY + itemsRef) rather than the scalar REF: an ArrayRefRow can hold
        // the multi-element case a scalar RefRow would silently truncate, and it
        // preserves the authored shape on round-trip (see ArrayRefRow.toJson).
        String scalarOrArrayRef = scalarOrArrayRef(p);
        if (ref == null && scalarOrArrayRef != null) {
            return new FormFieldSpec(name, FormFieldSpec.Kind.ARRAY, required, def,
                List.of(), null, null, desc, null, scalarOrArrayRef, List.of(), null, true);
        }

        List<String> enumVals = new ArrayList<>();
        if (p.has("enum")) for (JsonElement e : p.getAsJsonArray("enum")) enumVals.add(e.getAsString());

        Double min = null, max = null;
        if (p.has("minimum")) min = p.get("minimum").getAsDouble();
        if (p.has("exclusiveMinimum")) min = p.get("exclusiveMinimum").getAsDouble();
        if (p.has("maximum")) max = p.get("maximum").getAsDouble();
        if (p.has("exclusiveMaximum")) max = p.get("exclusiveMaximum").getAsDouble();

        FormFieldSpec.Kind kind;
        if (ref != null) {
            kind = FormFieldSpec.Kind.REF;
        } else if (!enumVals.isEmpty()) {
            kind = FormFieldSpec.Kind.ENUM;
        } else if (p.has("oneOf")) {
            kind = FormFieldSpec.Kind.MIXED; // e.g. name: string | object
        } else if (p.has("type")) {
            kind = switch (p.get("type").getAsString()) {
                case "string"  -> FormFieldSpec.Kind.STRING;
                case "integer" -> FormFieldSpec.Kind.INTEGER;
                case "number"  -> FormFieldSpec.Kind.NUMBER;
                case "boolean" -> FormFieldSpec.Kind.BOOLEAN;
                case "array"   -> FormFieldSpec.Kind.ARRAY;
                case "object"  -> FormFieldSpec.Kind.OBJECT;
                default        -> FormFieldSpec.Kind.UNKNOWN;
            };
        } else {
            kind = FormFieldSpec.Kind.UNKNOWN;
        }

        // For an array of REFs, capture items.$ref so the creator can render an
        // ArrayRefRow list editor; for an array of fixed-shape OBJECTS (e.g.
        // starting_equipment.stacks, enchantments) capture the element's field
        // list as `children` so an ArrayObjectRow renders a structured +/- list
        // instead of a raw-JSON box. An OBJECT with a FIXED set of inline
        // `properties` (item stack, effect instance, hud_render) captures the
        // same way for an inline sub-form. For an array of plain STRINGS capture
        // items.pattern as itemPattern, which is what marks the field a scalar-string
        // list and gets it an ArrayStringRow (a +/- list of text boxes) instead of a
        // raw-JSON box; without this every schema-derived array read that null and
        // ArrayStringRow was unreachable. Free-form objects / arrays that match none
        // of the three keep an empty child list and fall to the raw-JSON box.
        String itemsRef = null;
        String itemPattern = null;
        List<FormFieldSpec> children = List.of();
        if (kind == FormFieldSpec.Kind.ARRAY && p.has("items") && p.get("items").isJsonObject()) {
            JsonObject items = p.getAsJsonObject("items");
            if (items.has("$ref")) {
                itemsRef = items.get("$ref").getAsString();
            } else if (items.has("properties")) {
                children = mapChildren(items);
            } else if (items.has("pattern") && items.get("pattern").isJsonPrimitive()) {
                itemPattern = items.get("pattern").getAsString();
            }
        } else if (kind == FormFieldSpec.Kind.OBJECT && p.has("properties")) {
            children = mapChildren(p);
        }
        // STRING pattern — the same constraint the web editor enforces off this
        // schema; carried so the in-game text row can validate as it edits.
        String pattern = kind == FormFieldSpec.Kind.STRING
            && p.has("pattern") && p.get("pattern").isJsonPrimitive()
            ? p.get("pattern").getAsString() : null;
        return new FormFieldSpec(name, kind, required, def, enumVals, min, max, desc, ref, itemsRef, children,
            itemPattern, false, pattern);
    }

    /**
     * Detect the scalar-or-array {@code $ref} shape and return the shared
     * {@code $ref} target, or {@code null} if this node is not that shape.
     *
     * <p>Matches exactly {@code {"oneOf":[{"$ref":X},{"type":"array","items":{"$ref":X}}]}}
     * — two branches, one a bare {@code $ref}, the other an array whose
     * {@code items.$ref} names the SAME document. Branch order is not assumed.
     * Anything else (the string|object {@code MIXED} idiom, a {@code oneOf} of
     * three branches, two refs to different docs) falls through to the existing
     * handling untouched.
     */
    private static String scalarOrArrayRef(JsonObject p) {
        if (!p.has("oneOf") || !p.get("oneOf").isJsonArray()) return null;
        JsonArray branches = p.getAsJsonArray("oneOf");
        if (branches.size() != 2) return null;

        String single = null;
        String arrayItems = null;
        for (JsonElement be : branches) {
            if (!be.isJsonObject()) return null;
            JsonObject b = be.getAsJsonObject();
            if (b.has("$ref") && b.get("$ref").isJsonPrimitive()) {
                if (single != null) return null; // two scalar refs — not our shape
                single = b.get("$ref").getAsString();
            } else if (b.has("type") && b.get("type").isJsonPrimitive()
                    && "array".equals(b.get("type").getAsString())
                    && b.has("items") && b.get("items").isJsonObject()) {
                JsonObject items = b.getAsJsonObject("items");
                if (!items.has("$ref") || !items.get("$ref").isJsonPrimitive()) return null;
                if (arrayItems != null) return null;
                arrayItems = items.get("$ref").getAsString();
            } else {
                return null;
            }
        }
        return (single != null && single.equals(arrayItems)) ? single : null;
    }

    /** Map an object node's {@code properties} into child {@link FormFieldSpec}s. */
    private static List<FormFieldSpec> mapChildren(JsonObject objNode) {
        JsonObject objProps = objNode.getAsJsonObject("properties");
        Set<String> childReq = readRequired(objNode);
        List<FormFieldSpec> kids = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : objProps.entrySet()) {
            if (e.getKey().equals("type")) continue;
            kids.add(mapProperty(e.getKey(), e.getValue().getAsJsonObject(),
                childReq.contains(e.getKey())));
        }
        return kids;
    }

    private static Object unwrap(JsonElement e) {
        if (e.isJsonPrimitive()) {
            var pr = e.getAsJsonPrimitive();
            if (pr.isBoolean()) return pr.getAsBoolean();
            if (pr.isNumber()) return pr.getAsNumber();
            return pr.getAsString();
        }
        return e.toString();
    }

    // ── Accessors ───────────────────────────────────────────────────────────

    public Set<String> allTypes() { return allTypes; }

    public List<FormFieldSpec> commonFields() { return commonFields; }

    public boolean hasStructuredForm(String powerId) { return structured.containsKey(powerId); }

    /**
     * The structured field list for a power, or just the common fields (the
     * fallback branch contributes no fields) when it has no structured branch.
     */
    public List<FormFieldSpec> formFor(String powerId) {
        return structured.getOrDefault(powerId, commonFields);
    }

    /** powerIds that have a structured branch. */
    public Set<String> structuredTypes() { return structured.keySet(); }
}
