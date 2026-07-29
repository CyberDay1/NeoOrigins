package com.cyberday1.neoorigins.compat.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Small shared helpers for the compat JSON parsers.
 *
 * <p>Scoped deliberately: this captures only the most-duplicated getter idioms
 * ({@link #getOrNull} and {@link #parseArrayOrSingle}) and is applied to the
 * clearest named clusters in {@code ActionParser} / {@code ConditionParser}. A
 * broader project-wide sweep of the 130+ raw call-sites can follow later — it is
 * intentionally out of scope for this refactor.
 */
public final class JsonHelpers {

    private JsonHelpers() {}

    /**
     * Returns the child object at {@code key} when present and an object,
     * otherwise {@code null}. Captures the repeated
     * {@code o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null}
     * idiom.
     */
    public static JsonObject getOrNull(JsonObject o, String key) {
        if (o == null || !o.has(key)) return null;
        JsonElement el = o.get(key);
        return el.isJsonObject() ? el.getAsJsonObject() : null;
    }

    /**
     * Returns {@code key} as a {@link JsonArray}, coercing a lone object into a
     * one-element array and any other shape (absent, primitive, null) into an
     * empty one. Never throws.
     *
     * <p>Exists because the {@code and}/{@code or} combinators are written by
     * hand in legacy packs and a single-child combinator is routinely authored
     * as {@code "actions": { … }} rather than {@code "actions": [ { … } ]}.
     * Origins++ does this inside {@code raycast.before_action}; the raw
     * {@code getAsJsonArray} threw, which failed the whole enclosing action to a
     * no-op and silently killed the power. Coercing is a strict widening: a
     * well-formed array parses exactly as before.
     */
    public static JsonArray asArray(JsonObject o, String key) {
        if (o == null || !o.has(key)) return new JsonArray();
        JsonElement el = o.get(key);
        if (el.isJsonArray()) return el.getAsJsonArray();
        if (el.isJsonObject()) {
            JsonArray one = new JsonArray();
            one.add(el);
            return one;
        }
        return new JsonArray();
    }

    /**
     * Parse a field that may be absent, a single object, or an array of objects,
     * into a single combined result {@code R}.
     *
     * <ul>
     *   <li>absent (or a non-object/array primitive) → {@code absentDefault}</li>
     *   <li>single object → {@code parse.apply(obj, contextId)}</li>
     *   <li>array → each object element parsed, then folded via {@code combine}</li>
     * </ul>
     *
     * <p>This is the shared shape behind {@code ActionParser.parseField} and
     * {@code ConditionParser.parseField}: the two differ only in their element
     * type ({@code EntityAction} / {@code EntityCondition}), their absent default
     * (noop / alwaysTrue), and how a list is folded (sequential-run / logical-AND).
     * Non-object array elements are skipped, matching the originals.
     *
     * @param parent        the enclosing JSON object (may be {@code null})
     * @param field         field name to read
     * @param contextId     context id threaded to {@code parse} for diagnostics
     * @param absentDefault value returned when the field is absent / not an object or array
     * @param parse         parses a single JSON object (plus context id) into {@code R}
     * @param combine       folds the parsed list into one {@code R}; receives the
     *                      element list (never empty when invoked)
     */
    public static <R> R parseArrayOrSingle(
            JsonObject parent,
            String field,
            String contextId,
            R absentDefault,
            BiFunction<JsonObject, String, R> parse,
            Function<List<R>, R> combine) {
        if (parent == null || !parent.has(field)) return absentDefault;
        JsonElement el = parent.get(field);
        if (el.isJsonObject()) {
            return parse.apply(el.getAsJsonObject(), contextId);
        }
        if (el.isJsonArray()) {
            List<R> list = new ArrayList<>();
            JsonArray arr = el.getAsJsonArray();
            for (JsonElement item : arr) {
                if (item.isJsonObject()) list.add(parse.apply(item.getAsJsonObject(), contextId));
            }
            return combine.apply(list);
        }
        return absentDefault;
    }
}
