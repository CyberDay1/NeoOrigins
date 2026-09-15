package com.cyberday1.neoorigins.dev;

import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec.Kind;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code options()} used to be read only by the ENUM arm of the schema generator, so a
 * MIXED field could declare its legal strings and the schema would not say so — which
 * is why {@code render_elytra}'s tri-state degraded to a free-text box in both editors.
 *
 * <p>MIXED now honours {@code options()} on its STRING arm, and only there: the other
 * arms keep their own spelling, and a MIXED spec that declares no options must emit
 * exactly what it emitted before (pinned below with the {@code key} field's shape,
 * whose integer|string|object union is genuinely open and must stay a raw-JSON box).
 */
class MixedOptionsSchemaTest {

    private static String node(FieldSpec spec) {
        JsonObject n = SchemaNodeBuilder.buildNode(spec);
        return n.toString();
    }

    @Test
    void mixedWithOptionsConstrainsOnlyTheStringArm() {
        assertEquals(
            "{\"oneOf\":[{\"type\":\"boolean\"},"
                + "{\"type\":\"string\",\"enum\":[\"never\",\"flying\",\"always\"]}]}",
            node(new FieldSpec("render_elytra", Kind.MIXED, false)
                .mixedTypes("boolean", "string")
                .options("never", "flying", "always")));
    }

    @Test
    void optionsAreEmittedInDeclaredOrderNotSorted() {
        assertEquals(
            "{\"oneOf\":[{\"type\":\"string\",\"enum\":[\"c\",\"a\",\"b\"]}]}",
            node(new FieldSpec("x", Kind.MIXED, false).mixedTypes("string").options("c", "a", "b")));
    }

    @Test
    void mixedWithoutOptionsIsUnchanged() {
        assertEquals(
            "{\"oneOf\":[{\"type\":\"integer\"},{\"type\":\"string\"},{\"type\":\"object\"}]}",
            node(new FieldSpec("key", Kind.MIXED, false).mixedTypes("integer", "string", "object")),
            "a MIXED spec that declares no options must emit bare arms, exactly as before");
        assertEquals(
            "{\"oneOf\":[{\"type\":\"string\"},{\"type\":\"object\"}]}",
            node(new FieldSpec("name", Kind.MIXED, false)),
            "the default string|object idiom is untouched too");
    }

    @Test
    void enumFieldsAreUnaffected() {
        assertEquals(
            "{\"type\":\"string\",\"enum\":[\"add_value\",\"add_multiplied_base\"]}",
            node(new FieldSpec("operation", Kind.ENUM, false)
                .options("add_value", "add_multiplied_base")));
    }
}
