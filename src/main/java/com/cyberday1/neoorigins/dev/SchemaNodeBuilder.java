package com.cyberday1.neoorigins.dev;

import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec.Kind;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared JSON-Schema node/branch emitter for the registry-driven schema
 * generators ({@link PowerSchemaGenerator} and
 * {@link ActionConditionSchemaGenerator}).
 *
 * <p>Extracted verbatim from {@code PowerSchemaGenerator} so all three documents
 * (power / action / condition) emit identical field-node shapes from a single
 * place — the registry's "one FieldSpec → one schema node" contract lives here.
 * The power generator routes single-id branches through {@link #buildBranch} with
 * a one-element id list (emitting {@code type.const}); the action/condition
 * generator passes the canonical id plus its aliases / {@code apace:} variants
 * (emitting {@code type.enum}). Both are parsed back identically by
 * {@code SchemaFormModel} (branches register against every id in their const/enum).
 *
 * <p>Determinism contract (inherited from D3): insertion-ordered
 * {@link JsonObject}/{@link JsonArray}, fixed per-node key order
 * ({@code description}, then {@code type}/{@code $ref}/{@code oneOf}/{@code enum},
 * then {@code default}, {@code minimum}/{@code maximum}, {@code items},
 * {@code properties}/{@code required}). Re-running a generator on its own output
 * must be byte-identical.
 */
public final class SchemaNodeBuilder {

    private SchemaNodeBuilder() {}

    /**
     * Build one structured {@code oneOf} branch.
     *
     * <p>Shape: {@code {"$comment":"<canonicalId>","properties":{"type":{<const|enum>},
     * <field>:<node>...},"required":["type", <required field names>...]}}.
     * {@code typeIds} is the full set of ids this branch matches (canonical first):
     * a single id emits {@code {"const":id}}; multiple ids emit {@code {"enum":[…]}}
     * in the given order. Marker-only branches (empty field list) emit just the
     * {@code type} discriminator with {@code required:["type"]}.
     */
    public static JsonObject buildBranch(String canonicalId, List<String> typeIds, List<FieldSpec> fields) {
        JsonObject branch = new JsonObject();
        branch.addProperty("$comment", canonicalId);

        JsonObject props = new JsonObject();
        JsonObject typeNode = new JsonObject();
        if (typeIds.size() == 1) {
            typeNode.addProperty("const", typeIds.get(0));
        } else {
            JsonArray enumArr = new JsonArray();
            for (String id : typeIds) enumArr.add(id);
            typeNode.add("enum", enumArr);
        }
        props.add("type", typeNode);

        List<String> required = new ArrayList<>();
        required.add("type");
        for (FieldSpec fs : fields) {
            props.add(fs.name(), buildNode(fs)); // keyed by JSON name (not component)
            if (fs.required()) required.add(fs.name());
        }
        branch.add("properties", props);

        JsonArray req = new JsonArray();
        for (String r : required) req.add(r);
        branch.add("required", req);
        return branch;
    }

    /**
     * Map one {@link FieldSpec} to its JSON-Schema node. Keys are emitted in this
     * fixed order per node: {@code description}, then {@code type}/{@code $ref}/
     * {@code oneOf}/{@code enum}, then {@code default}, then {@code minimum}/
     * {@code maximum}, then {@code items}, then {@code properties}/{@code required}.
     */
    public static JsonObject buildNode(FieldSpec fs) {
        JsonObject node = new JsonObject();
        Kind kind = fs.kind();

        // description first (common to leaves).
        if (fs.description() != null) node.addProperty("description", fs.description());

        switch (kind) {
            case STRING -> node.addProperty("type", "string");
            case INTEGER -> node.addProperty("type", "integer");
            case NUMBER -> node.addProperty("type", "number");
            case BOOLEAN -> node.addProperty("type", "boolean");
            case ENUM -> {
                node.addProperty("type", "string");
                JsonArray values = new JsonArray();
                for (String v : fs.enumValues()) values.add(v); // declared order, NOT sorted
                node.add("enum", values);
            }
            case OBJECT -> {
                node.addProperty("type", "object");
            }
            case ARRAY -> {
                node.addProperty("type", "array");
            }
            case REF -> {
                if (fs.ref() != null) node.addProperty("$ref", fs.ref());
            }
            case MIXED -> {
                JsonArray oneOf = new JsonArray();
                JsonObject asString = new JsonObject();
                asString.addProperty("type", "string");
                JsonObject asObject = new JsonObject();
                asObject.addProperty("type", "object");
                oneOf.add(asString);
                oneOf.add(asObject);
                node.add("oneOf", oneOf);
            }
            case UNKNOWN -> {
                // {} (plus any description already added).
            }
        }

        // pattern (regex) — STRING fields only; a format hint surfaced by the
        // web editor (StringFieldSpec.pattern). After type, before default.
        if (kind == Kind.STRING && fs.pattern() != null) {
            node.addProperty("pattern", fs.pattern());
        }

        // default (typed) — after type/$ref/oneOf/enum.
        if (fs.defaultValue() != null) {
            Object d = fs.defaultValue();
            if (d instanceof Boolean b) node.addProperty("default", b);
            else if (d instanceof Number n) node.addProperty("default", n);
            else node.addProperty("default", d.toString());
        }

        // minimum / maximum — integral when INTEGER.
        if (fs.min() != null) {
            if (kind == Kind.INTEGER) node.addProperty("minimum", (long) (double) fs.min());
            else node.addProperty("minimum", fs.min());
        }
        if (fs.max() != null) {
            if (kind == Kind.INTEGER) node.addProperty("maximum", (long) (double) fs.max());
            else node.addProperty("maximum", fs.max());
        }

        // items — for ARRAY. A list of $ref elements (and/or.conditions,
        // and.actions) emits items.$ref from FieldSpec.itemsRef; a scalar-string
        // list (no itemsRef, but an itemPattern) emits items:{type:string,pattern};
        // a permissive array (neither) emits the permissive {}.
        if (kind == Kind.ARRAY) {
            JsonObject items = new JsonObject();
            if (fs.itemsRef() != null) {
                items.addProperty("$ref", fs.itemsRef());
            } else if (fs.itemPattern() != null) {
                items.addProperty("type", "string");
                items.addProperty("pattern", fs.itemPattern());
            }
            node.add("items", items);
        }

        // properties / required — for OBJECT with children (real OR virtual:
        // identical in JSON).
        if (kind == Kind.OBJECT && !fs.children().isEmpty()) {
            JsonObject childProps = new JsonObject();
            List<String> childRequired = new ArrayList<>();
            for (FieldSpec child : fs.children()) {
                childProps.add(child.name(), buildNode(child));
                if (child.required()) childRequired.add(child.name());
            }
            node.add("properties", childProps);
            if (!childRequired.isEmpty()) {
                JsonArray req = new JsonArray();
                for (String r : childRequired) req.add(r);
                node.add("required", req);
            }
        }

        return node;
    }
}
