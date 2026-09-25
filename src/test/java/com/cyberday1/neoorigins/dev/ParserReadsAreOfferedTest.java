package com.cyberday1.neoorigins.dev;

import com.cyberday1.neoorigins.compat.action.ActionParser;
import com.cyberday1.neoorigins.compat.action.BuiltinActions;
import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.power.builtin.PersistentEffectPower;
import com.cyberday1.neoorigins.power.registry.BuiltinPowers;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every root key a parser reads must be a field both editors offer, or a named synonym.
 * Reads are observed, not grepped: the probe object records each key the parser looks up.
 */
// hub: neoorigins/editor-parser-drift.md
class ParserReadsAreOfferedTest {

    @Test
    void persistentEffectRootShorthandIsOffered() {
        Set<String> seen = new TreeSet<>();
        JsonObject json = recording(seen);
        json.addProperty("type", "neoorigins:persistent_effect");
        PersistentEffectPower.Config.CODEC.parse(SAME_OBJECT, json);

        assertEquals(Set.of(), unoffered(seen, BuiltinPowers.fieldsFor("neoorigins:persistent_effect"),
            Set.of("type", "id")), "persistent_effect reads keys neither editor offers");
    }

    @Test
    void projectileRainReadsAreOffered() {
        Set<String> seen = new TreeSet<>();
        JsonObject json = recording(seen);
        json.addProperty("type", "neoorigins:spawn_projectile_rain");
        ActionParser.parse(json, "test");

        assertEquals(Set.of(), unoffered(seen, BuiltinActions.get("neoorigins:spawn_projectile_rain").fields(),
            Set.of("type", "sword_count", "damage_per_sword")), "spawn_projectile_rain reads keys neither editor offers");
    }

    private static Set<String> unoffered(Set<String> seen, List<FieldSpec> offered, Set<String> synonyms) {
        Set<String> out = new TreeSet<>(seen);
        offered.forEach(f -> out.remove(f.name()));
        out.removeAll(synonyms);
        return out;
    }

    /** A JsonObject whose key lookups land in {@code seen}; gson routes every get/has through the map comparator. */
    private static JsonObject recording(Set<String> seen) {
        JsonObject o = new JsonObject();
        Comparator<Object> c = (a, b) -> {
            if (a instanceof String s) seen.add(s);
            @SuppressWarnings("unchecked") int v = ((Comparable<Object>) a).compareTo(b);
            return v;
        };
        try {
            Field members = JsonObject.class.getDeclaredField("members");
            members.setAccessible(true);
            members.set(o, new LinkedTreeMap<String, JsonElement>(c, false));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("gson changed its JsonObject layout", e);
        }
        return o;
    }

    /** Hand-rolled codecs convert to JsonOps first; hand them the recording object itself, not a copy. */
    private static final JsonOps SAME_OBJECT = new JsonOps(false) {
        @Override
        @SuppressWarnings("unchecked")
        public <U> U convertTo(DynamicOps<U> outOps, JsonElement input) {
            return outOps instanceof JsonOps ? (U) input : super.convertTo(outOps, input);
        }
    };
}
