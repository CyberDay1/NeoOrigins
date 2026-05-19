package com.cyberday1.neoorigins.power.schemaform;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-power, per-field plain-language documentation for the 2.1 creator.
 *
 * <p>Many powers have no structured {@code power.schema.json} branch, so their
 * codec-reflected fields would otherwise show no help. This loads the curated
 * {@code field_docs.json} ({@code "<neoorigins:type>": { "<field>": "…" }}
 * with a {@code "*"} block for fields that mean the same thing everywhere) and
 * is the description source {@link FormModel} falls back to. Its completeness
 * is enforced by {@code SchemaFormCheck} — every form field of every builtin
 * power must resolve a description here or in the schema, or the build fails.
 */
public final class FieldDocs {

    public static final String RESOURCE_PATH = "/data/neoorigins/schema/field_docs.json";
    private static final String WILDCARD = "*";

    private static volatile FieldDocs instance;

    private final Map<String, Map<String, String>> byType = new ConcurrentHashMap<>();

    private FieldDocs() {}

    public static FieldDocs get() {
        FieldDocs r = instance;
        if (r == null) {
            synchronized (FieldDocs.class) {
                r = instance;
                if (r == null) instance = r = loadFromClasspath();
            }
        }
        return r;
    }

    private static FieldDocs loadFromClasspath() {
        try (InputStream in = FieldDocs.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new UncheckedIOException(new IOException(
                    "field_docs.json not on classpath at " + RESOURCE_PATH
                        + " — build processResources copy missing"));
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            FieldDocs fd = new FieldDocs();
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (var typeEntry : root.entrySet()) {
                JsonObject fields = typeEntry.getValue().getAsJsonObject();
                Map<String, String> m = new ConcurrentHashMap<>();
                for (var fe : fields.entrySet()) m.put(fe.getKey(), fe.getValue().getAsString());
                fd.byType.put(typeEntry.getKey(), m);
            }
            return fd;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Description for {@code field} on power {@code typeId} (e.g.
     * {@code "neoorigins:particle"}): the power-specific entry if present,
     * else the shared {@code "*"} entry, else {@code null}.
     */
    public String describe(String typeId, String field) {
        Map<String, String> perType = byType.get(typeId);
        if (perType != null && perType.containsKey(field)) return perType.get(field);
        Map<String, String> shared = byType.get(WILDCARD);
        return shared == null ? null : shared.get(field);
    }
}
