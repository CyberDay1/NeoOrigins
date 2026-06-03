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
    /** Every power id appearing in the root {@code type} enum (the universe). */
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

        // Universe of types from properties.type.enum
        JsonArray typeEnum = props.getAsJsonObject("type").getAsJsonArray("enum");
        for (JsonElement e : typeEnum) allTypes.add(e.getAsString());

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
            for (String id : branchIds) structured.put(id, fields);
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

        // For an array of REFs, capture items.$ref so the creator can render
        // an ArrayRefRow list editor instead of a raw-JSON textbox.
        String itemsRef = null;
        if (kind == FormFieldSpec.Kind.ARRAY && p.has("items")) {
            JsonObject items = p.getAsJsonObject("items");
            if (items.has("$ref")) itemsRef = items.get("$ref").getAsString();
        }

        // An OBJECT with a FIXED set of inline `properties` (e.g. an item stack,
        // an effect instance, hud_render) → render an inline sub-form: recurse
        // into each child property the same way. Free-form objects (no
        // `properties`) keep an empty child list and fall to the raw-JSON box.
        List<FormFieldSpec> children = List.of();
        if (kind == FormFieldSpec.Kind.OBJECT && p.has("properties")) {
            JsonObject objProps = p.getAsJsonObject("properties");
            Set<String> childReq = readRequired(p);
            List<FormFieldSpec> kids = new ArrayList<>();
            for (Map.Entry<String, JsonElement> e : objProps.entrySet()) {
                if (e.getKey().equals("type")) continue;
                kids.add(mapProperty(e.getKey(), e.getValue().getAsJsonObject(),
                    childReq.contains(e.getKey())));
            }
            children = kids;
        }
        return new FormFieldSpec(name, kind, required, def, enumVals, min, max, desc, ref, itemsRef, children);
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
