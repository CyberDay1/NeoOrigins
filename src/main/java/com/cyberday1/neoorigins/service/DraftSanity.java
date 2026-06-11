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
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

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

    /** JSON field name → registry lookup for unknown-id checks. Resolves
     *  against the live {@link RegistryAccess} (composes built-in + modded +
     *  datapack/dynamic registries) rather than vanilla-only
     *  {@code BuiltInRegistries}, so a valid modded/datapack id is not
     *  false-flagged as a hard Save error. If the registry isn't available
     *  (e.g. {@code RegistryAccess.EMPTY}), degrade to "don't flag". */
    private static boolean idExists(RegistryAccess ra, String field, String value) {
        ResourceLocation rl;
        try { rl = ResourceLocation.parse(value); } catch (RuntimeException e) { return false; }
        try {
            return switch (field) {
                case "particle", "particle_type" -> ra.lookupOrThrow(Registries.PARTICLE_TYPE)
                    .get(ResourceKey.create(Registries.PARTICLE_TYPE, rl)).isPresent();
                case "sound", "sound_event" -> ra.lookupOrThrow(Registries.SOUND_EVENT)
                    .get(ResourceKey.create(Registries.SOUND_EVENT, rl)).isPresent();
                case "block" -> ra.lookupOrThrow(Registries.BLOCK)
                    .get(ResourceKey.create(Registries.BLOCK, rl)).isPresent();
                case "item" -> ra.lookupOrThrow(Registries.ITEM)
                    .get(ResourceKey.create(Registries.ITEM, rl)).isPresent();
                case "entity", "entity_type" -> ra.lookupOrThrow(Registries.ENTITY_TYPE)
                    .get(ResourceKey.create(Registries.ENTITY_TYPE, rl)).isPresent();
                // Attributes need the same prefix tolerance the loader applies
                // (1.21.1 registers vanilla attributes as generic.*/player.*, but
                // pack JSON writes the de-prefixed form). Delegate to the shared
                // resolver so the Save gate matches what AttributeModifierPower
                // actually accepts at load.
                case "attribute" -> com.cyberday1.neoorigins.power.builtin.AttributeModifierPower
                    .attributeResolvable(rl);
                case "effect", "status_effect", "mob_effect" -> ra.lookupOrThrow(Registries.MOB_EFFECT)
                    .get(ResourceKey.create(Registries.MOB_EFFECT, rl)).isPresent();
                default -> true; // not an id field we check
            };
        } catch (RuntimeException e) {
            return true; // registry unavailable (e.g. RegistryAccess.EMPTY) — don't false-flag
        }
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
    public static List<String> powerProblems(RegistryAccess ra, OriginDraft draft) {
        List<String> out = new ArrayList<>();
        int i = 0;
        for (PowerDraft p : draft.powers) {
            i++;
            String tag = "power #" + i;
            ResourceLocation type;
            try {
                type = ResourceLocation.parse(p.typeId);
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
            scanIds(ra, body, tag, out);
        }
        return out;
    }

    /** Recursively flag unknown registry ids and unknown neoorigins: type refs. */
    private static void scanIds(RegistryAccess ra, JsonElement el, String tag, List<String> out) {
        if (el.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                String k = e.getKey();
                JsonElement v = e.getValue();
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                    String val = v.getAsString();
                    if (isCheckedIdField(k) && !idExists(ra, k, val)) {
                        out.add(tag + ": " + k + " \"" + val + "\" is not a registered id");
                    }
                    if (k.equals("type") && val.startsWith("neoorigins:")
                            && !knownTypeRef(val)) {
                        out.add(tag + ": unknown type \"" + val + "\" (typo? not a power/"
                            + "condition/action)");
                    }
                } else {
                    scanIds(ra, v, tag, out);
                }
            }
        } else if (el.isJsonArray()) {
            for (JsonElement c : (JsonArray) el) scanIds(ra, c, tag, out);
        }
    }

    private static boolean knownTypeRef(String id) {
        if (ConditionParser.KNOWN_TYPES.contains(id)
                || ActionParser.KNOWN_TYPES.contains(id)) return true;
        try { return PowerTypes.get(ResourceLocation.parse(id)) != null; }
        catch (RuntimeException e) { return false; }
    }

    /**
     * BLOCKING client-side pre-save problems: id path + layer + per-power.
     * This is the subset the in-game creator's Save button gates on — every
     * entry here would also be rejected by the server gate, so blocking
     * client-side never refuses a draft the server would accept.
     */
    public static List<String> blockingProblems(RegistryAccess ra, OriginDraft draft) {
        List<String> out = new ArrayList<>();
        try {
            draft.originId();
        } catch (RuntimeException e) {
            out.add("id path \"" + draft.idPath
                + "\" is not valid (lowercase a-z, 0-9, _, /, -)");
        }
        if (draft.layerId == null) out.add("no target layer set");
        out.addAll(powerProblems(ra, draft));
        return out;
    }

    /** Full client-side pre-save check: {@link #blockingProblems} + advisory
     *  warnings (shown in the JSON-tab problems panel, but never Save-blocking). */
    public static List<String> draftProblems(RegistryAccess ra, OriginDraft draft) {
        List<String> out = blockingProblems(ra, draft);
        if (draft.powers.isEmpty()) out.add("origin has no powers (it will do nothing)");
        return out;
    }
}
