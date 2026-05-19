package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.action.ActionParser;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.power.registry.PowerTypes;
import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec;
import com.cyberday1.neoorigins.power.schemaform.FormModel;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft.PowerDraft;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Environment-neutral "does this draft actually make sense" checks, shared by
 * the server gate ({@link CreatorValidator}) and the client problems panel so
 * both report the exact same issues. Goes deeper than "is it JSON": each power
 * is parsed through its real codec, required fields are checked against the
 * form model, and id-shaped values are verified against the live registries /
 * the parser vocabularies so typos that would silently no-op are surfaced.
 *
 * <p>Must run in-game (uses live registries and the condition/action parser
 * vocab); not headless-safe by design.
 */
public final class DraftSanity {

    private DraftSanity() {}

    /** JSON field name → BuiltInRegistries lookup for unknown-id checks. */
    private static boolean idExists(String field, String value) {
        Identifier rl;
        try { rl = Identifier.parse(value); } catch (RuntimeException e) { return false; }
        return switch (field) {
            case "particle", "particle_type" -> BuiltInRegistries.PARTICLE_TYPE.containsKey(rl);
            case "sound", "sound_event"      -> BuiltInRegistries.SOUND_EVENT.containsKey(rl);
            case "block"                     -> BuiltInRegistries.BLOCK.containsKey(rl);
            case "item"                      -> BuiltInRegistries.ITEM.containsKey(rl);
            case "entity", "entity_type"     -> BuiltInRegistries.ENTITY_TYPE.containsKey(rl);
            case "attribute"                 -> BuiltInRegistries.ATTRIBUTE.containsKey(rl);
            case "effect", "status_effect", "mob_effect"
                                             -> BuiltInRegistries.MOB_EFFECT.containsKey(rl);
            default -> true; // not an id field we check
        };
    }

    private static boolean isCheckedIdField(String field) {
        return switch (field) {
            case "particle", "particle_type", "sound", "sound_event", "block", "item",
                 "entity", "entity_type", "attribute", "effect", "status_effect",
                 "mob_effect" -> true;
            default -> false;
        };
    }

    /** Per-power deep checks (used by both the server gate and client panel). */
    public static List<String> powerProblems(OriginDraft draft) {
        List<String> out = new ArrayList<>();
        int i = 0;
        for (PowerDraft p : draft.powers) {
            i++;
            String tag = "power #" + i;
            Identifier type;
            try {
                type = Identifier.parse(p.typeId);
            } catch (RuntimeException e) {
                out.add(tag + ": invalid type id \"" + p.typeId + "\"");
                continue;
            }
            tag = "power #" + i + " (" + type.getPath() + ")";
            PowerType<?> pt = PowerTypes.get(type);
            if (pt == null) { out.add(tag + ": unknown power type " + type); continue; }

            JsonObject body;
            try {
                JsonElement el = JsonParser.parseString(
                    p.rawJson == null || p.rawJson.isBlank() ? "{}" : p.rawJson);
                if (!el.isJsonObject()) { out.add(tag + ": body must be a JSON object"); continue; }
                body = el.getAsJsonObject();
            } catch (RuntimeException e) {
                out.add(tag + ": body is not valid JSON"); continue;
            }

            // Deep: parse the full power JSON through its real codec.
            JsonObject withType = CustomPackSerializer.powerJson(p);
            final String ftag = tag;
            pt.codec().parse(JsonOps.INSTANCE, withType).error().ifPresent(err ->
                out.add(ftag + ": " + err.message()));

            // Required fields present (per the form model for this type).
            try {
                for (FormFieldSpec s : FormModel.forPower(type)) {
                    if (s.required() && !s.name().equals("type") && !body.has(s.name())) {
                        out.add(tag + ": missing required field \"" + s.name() + "\"");
                    }
                }
            } catch (RuntimeException ignored) { /* form model unavailable — skip */ }

            // Unknown ids: registry-backed string fields + neoorigins: type refs.
            scanIds(body, tag, out);
        }
        return out;
    }

    /** Recursively flag unknown registry ids and unknown neoorigins: type refs. */
    private static void scanIds(JsonElement el, String tag, List<String> out) {
        if (el.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                String k = e.getKey();
                JsonElement v = e.getValue();
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                    String val = v.getAsString();
                    if (isCheckedIdField(k) && !idExists(k, val)) {
                        out.add(tag + ": " + k + " \"" + val + "\" is not a registered id");
                    }
                    if (k.equals("type") && val.startsWith("neoorigins:")
                            && !knownTypeRef(val)) {
                        out.add(tag + ": unknown type \"" + val + "\" (typo? not a power/"
                            + "condition/action)");
                    }
                } else {
                    scanIds(v, tag, out);
                }
            }
        } else if (el.isJsonArray()) {
            for (JsonElement c : (JsonArray) el) scanIds(c, tag, out);
        }
    }

    private static boolean knownTypeRef(String id) {
        if (ConditionParser.KNOWN_TYPES.contains(id)
                || ActionParser.KNOWN_TYPES.contains(id)) return true;
        try { return PowerTypes.get(Identifier.parse(id)) != null; }
        catch (RuntimeException e) { return false; }
    }

    /** Full client-side pre-save check: id path + layer + per-power. */
    public static List<String> draftProblems(OriginDraft draft) {
        List<String> out = new ArrayList<>();
        try {
            draft.originId();
        } catch (RuntimeException e) {
            out.add("id path \"" + draft.idPath
                + "\" is not valid (lowercase a-z, 0-9, _, /, -)");
        }
        if (draft.layerId == null) out.add("no target layer set");
        if (draft.powers.isEmpty()) out.add("origin has no powers (it will do nothing)");
        out.addAll(powerProblems(draft));
        return out;
    }
}
