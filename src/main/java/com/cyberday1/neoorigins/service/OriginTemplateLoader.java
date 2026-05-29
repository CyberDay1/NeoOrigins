package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.cyberday1.neoorigins.screen.creator.model.OriginTemplate;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

/**
 * Fills an {@link OriginDraft} from an {@link OriginTemplate} so the in-game
 * creator can offer "Load template" as a one-click starting point. Splits the
 * source origin's namespace and idPath so a templated vanilla origin saves
 * back over the shipped one (the user's stated reason for offering templates
 * in the first place); the picker UI surfaces this via the id field's
 * override indicator.
 *
 * <p>Power ids are deliberately rewritten to {@code OriginDraft.CUSTOM_NAMESPACE}
 * — keeping the source namespace would mean the templated draft's edits also
 * overwrite the vanilla power files, which is rarely what you want when
 * tweaking a single origin. The draft's origin body references the new ids
 * via the rewritten power list.
 */
public final class OriginTemplateLoader {

    private OriginTemplateLoader() {}

    /** Mutates {@code draft} in place — replaces identity, layer, and the
     *  entire power list with the template's contents. Existing draft state
     *  is discarded (the picker calls this on a fresh draft post-confirm). */
    public static void load(OriginTemplate template, OriginDraft draft) {
        ResourceLocation src = template.originId();
        draft.namespace = src.getNamespace();
        draft.idPath = src.getPath();
        draft.layerId = template.layerId();
        draft.icon = template.icon();
        draft.impact = template.impact();

        // Origin body: copy display fields and order through, then rewrite the
        // power list to the new (custom-namespace) ids. The on-disk writer
        // composes the origin JSON from the draft fields itself, so we only
        // need to populate the identity-level pieces here.
        try {
            JsonObject body = JsonParser.parseString(template.originBody()).getAsJsonObject();
            draft.name = readText(body, "name", src.getPath());
            draft.description = readText(body, "description", "");
            if (body.has("order") && body.get("order").isJsonPrimitive()) {
                try { draft.order = body.get("order").getAsInt(); }
                catch (NumberFormatException ignored) { /* keep default */ }
            }
        } catch (RuntimeException e) {
            draft.name = src.getPath();
            draft.description = "";
        }

        // Powers: clone every body, mint a custom-namespace id so future
        // edits stay scoped to the user's pack and don't shadow other
        // origins that reference the original power id.
        draft.powers.clear();
        for (var e : template.powers().entrySet()) {
            String typeId = extractType(e.getValue());
            OriginDraft.PowerDraft pd =
                new OriginDraft.PowerDraft(null /* set just below */, typeId);
            pd.powerId = draft.mintPowerId(pd, typeId);
            pd.rawJson = stripDisplayFields(e.getValue());
            draft.powers.add(pd);
        }
    }

    /** Extract a translation key / literal text from a name|description field
     *  that may be a string, {@code {"translate":"..."}}, or {@code {"text":"..."}}. */
    private static String readText(JsonObject body, String field, String fallback) {
        if (!body.has(field)) return fallback;
        var el = body.get(field);
        if (el.isJsonPrimitive()) return el.getAsString();
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("translate")) return o.get("translate").getAsString();
            if (o.has("text")) return o.get("text").getAsString();
        }
        return fallback;
    }

    private static String extractType(String rawJson) {
        try {
            JsonObject o = JsonParser.parseString(rawJson).getAsJsonObject();
            if (o.has("type") && o.get("type").isJsonPrimitive()) {
                return o.get("type").getAsString();
            }
        } catch (RuntimeException ignored) { /* malformed body — let user fix */ }
        return "";
    }

    /** Strip {@code id} (added by the loader, not part of the source body)
     *  so the cloned body matches what the Powers tab would save. Display
     *  fields (name/description) stay — the user usually wants to edit them. */
    private static String stripDisplayFields(String rawJson) {
        try {
            JsonObject o = JsonParser.parseString(rawJson).getAsJsonObject();
            o.remove("id");
            o.remove("_power_id");
            return o.toString();
        } catch (RuntimeException e) {
            return rawJson;
        }
    }
}
