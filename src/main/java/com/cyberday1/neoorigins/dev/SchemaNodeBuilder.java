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
        return buildBranch(canonicalId, typeIds, fields, false);
    }

    /**
     * As {@link #buildBranch(String, List, List)} but with control over whether
     * {@code action.schema.json}/{@code condition.schema.json} {@code $ref} fields
     * are widened to {@code oneOf:[ref, array-of-ref]}.
     *
     * <p>The power generator passes {@code widenActionConditionRefs=true}: native
     * power CODECs route those fields through {@code ActionParser.parseField} /
     * {@code ConditionParser.parseField}, which accept either a single object or a
     * JSON array (sequential actions / AND-combined conditions). The action/condition
     * generator passes {@code false} — array support there is expressed per-field via
     * each meta-action's own {@code items}/{@code itemsRef} (e.g. {@code and.actions}),
     * so a blanket widening would overstate support.
     */
    public static JsonObject buildBranch(String canonicalId, List<String> typeIds,
                                         List<FieldSpec> fields, boolean widenActionConditionRefs) {
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
            props.add(fs.name(), buildNode(fs, widenActionConditionRefs)); // keyed by JSON name (not component)
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
        return buildNode(fs, false);
    }

    /**
     * As {@link #buildNode(FieldSpec)} but, when {@code widenActionConditionRefs}
     * is set, a REF node pointing at {@code action.schema.json} or
     * {@code condition.schema.json} is emitted as
     * {@code oneOf:[ {$ref}, {type:array,items:{$ref}} ]} to advertise that the
     * native power CODEC accepts either a single object or an array (handled by
     * {@code ActionParser.parseField} / {@code ConditionParser.parseField}).
     */
    public static JsonObject buildNode(FieldSpec fs, boolean widenActionConditionRefs) {
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
                if (fs.ref() != null) {
                    if (widenActionConditionRefs
                            && ("action.schema.json".equals(fs.ref())
                                || "condition.schema.json".equals(fs.ref()))) {
                        // Native power CODECs accept a single object OR a JSON array
                        // of these (sequential actions / AND-combined conditions),
                        // via {Action,Condition}Parser.parseField.
                        JsonArray oneOf = new JsonArray();
                        JsonObject single = new JsonObject();
                        single.addProperty("$ref", fs.ref());
                        JsonObject asArray = new JsonObject();
                        asArray.addProperty("type", "array");
                        JsonObject items = new JsonObject();
                        items.addProperty("$ref", fs.ref());
                        asArray.add("items", items);
                        oneOf.add(single);
                        oneOf.add(asArray);
                        node.add("oneOf", oneOf);
                    } else {
                        node.addProperty("$ref", fs.ref());
                    }
                }
            }
            case MIXED -> {
                // The arms are the JSON types the parser actually accepts. Most
                // MIXED fields are the "id string or options object" idiom, which
                // stays the default; FieldSpec.mixedTypes widens the few that read
                // something else (e.g. key: number | string | object).
                List<String> arms = fs.mixedTypes().isEmpty()
                    ? List.of("string", "object")
                    : fs.mixedTypes();
                JsonArray oneOf = new JsonArray();
                for (String arm : arms) {
                    JsonObject asType = new JsonObject();
                    asType.addProperty("type", arm);
                    oneOf.add(asType);
                }
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
        // a list of fixed-shape OBJECTS (children on an ARRAY = one element's
        // shape) emits items:{type:object,properties,required} — without it the
        // permissive items:{} accepts [1,"x",null], so the web validator greens a
        // file the codec then hard-fails on at datapack load; a permissive array
        // (none of the three) still emits {}.
        if (kind == Kind.ARRAY) {
            JsonObject items = new JsonObject();
            if (fs.itemsRef() != null) {
                items.addProperty("$ref", fs.itemsRef());
            } else if (fs.itemPattern() != null) {
                items.addProperty("type", "string");
                items.addProperty("pattern", fs.itemPattern());
            } else if (!fs.children().isEmpty()) {
                items.addProperty("type", "object");
                addChildren(items, fs.children(), widenActionConditionRefs);
            }
            node.add("items", items);
        }

        // properties / required — for OBJECT with children (real OR virtual:
        // identical in JSON).
        if (kind == Kind.OBJECT && !fs.children().isEmpty()) {
            addChildren(node, fs.children(), widenActionConditionRefs);
        }

        return node;
    }

    /**
     * Emit {@code properties} (+ {@code required} when non-empty) for a child
     * field list onto {@code target}. Shared by the OBJECT node itself and by an
     * ARRAY's {@code items} sub-node, so the two shapes stay identical in JSON.
     */
    private static void addChildren(JsonObject target, List<FieldSpec> children,
                                    boolean widenActionConditionRefs) {
        JsonObject childProps = new JsonObject();
        List<String> childRequired = new ArrayList<>();
        for (FieldSpec child : children) {
            childProps.add(child.name(), buildNode(child, widenActionConditionRefs));
            if (child.required()) childRequired.add(child.name());
        }
        target.add("properties", childProps);
        if (!childRequired.isEmpty()) {
            JsonArray req = new JsonArray();
            for (String r : childRequired) req.add(r);
            target.add("required", req);
        }
    }
}
