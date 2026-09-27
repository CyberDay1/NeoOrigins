package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.cyberday1.neoorigins.screen.creator.model.OriginTemplate;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;

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
 * tweaking a single origin. References between the cloned powers, and the
 * origin's own keys the draft has no field for, are rewritten to the new ids.
 */
public final class OriginTemplateLoader {

    private OriginTemplateLoader() {}

    /** Keys the draft models itself; everything else rides in {@code draft.extras}. */
    private static final java.util.Set<String> MODELLED =
        java.util.Set.of("id", "name", "description", "icon", "impact", "order", "powers");

    /** Mutates {@code draft} in place — replaces identity, layer, and the
     *  entire power list with the template's contents. Existing draft state
     *  is discarded (the picker calls this on a fresh draft post-confirm). */
    public static void load(OriginTemplate template, OriginDraft draft) {
        Identifier src = template.originId();
        draft.namespace = src.getNamespace();
        draft.idPath = src.getPath();
        draft.layerId = template.layerId();
        draft.icon = template.icon();
        draft.impact = template.impact();

        JsonObject body;
        try {
            body = JsonParser.parseString(template.originBody()).getAsJsonObject();
        } catch (RuntimeException e) {
            body = new JsonObject();
        }
        draft.nameSource = body.get("name");
        draft.name = draft.nameShown = shownText(body, "name", src.getPath());
        draft.descriptionSource = body.get("description");
        draft.description = draft.descriptionShown = shownText(body, "description", "");
        if (body.has("order") && body.get("order").isJsonPrimitive()) {
            try { draft.order = body.get("order").getAsInt(); }
            catch (NumberFormatException ignored) { /* keep default */ }
        }

        // Clone every power under a custom-namespace id, so edits never touch a
        // shipped file other origins may share, then point every reference at
        // the clones: resource costs, power_active checks and tier_powers.
        draft.powers.clear();
        draft.tierPowers.clear();
        java.util.Map<String, String> renamed = new java.util.HashMap<>();
        java.util.List<java.util.Map.Entry<OriginDraft.PowerDraft, String>> cloned = new java.util.ArrayList<>();
        for (var list : java.util.List.of(template.powers(), template.tierPowers())) {
            boolean tier = list == template.tierPowers();
            for (var e : list.entrySet()) {
                OriginDraft.PowerDraft pd = new OriginDraft.PowerDraft(null, extractType(e.getValue()));
                (tier ? draft.tierPowers : draft.powers).add(pd);
                pd.powerId = cloneId(draft, pd, e.getKey());
                renamed.put(e.getKey().toString(), pd.powerId.toString());
                cloned.add(java.util.Map.entry(pd, e.getValue()));
            }
        }
        for (var c : cloned) c.getKey().rawJson = stripDisplayFields(c.getValue(), renamed);

        JsonObject extras = new JsonObject();
        for (var e : body.entrySet()) {
            if (!MODELLED.contains(e.getKey())) extras.add(e.getKey(), PowerIdRemap.rewrite(e.getValue(), renamed));
        }
        draft.extras = extras;
    }

    /** {@code neoorigins_custom:<source path>}, suffixed if another clone already has it. */
    private static Identifier cloneId(OriginDraft draft, OriginDraft.PowerDraft self, Identifier source) {
        String base = source.getPath(), path = base;
        for (int n = 2; draft.idTaken(self, path); n++) path = base + "_" + n;
        return Identifier.fromNamespaceAndPath(OriginDraft.CUSTOM_NAMESPACE, path);
    }

    /** The text a name/description shows in game: a translation key resolves through the language. */
    private static String shownText(JsonObject body, String field, String fallback) {
        if (!body.has(field)) return fallback;
        try {
            return com.cyberday1.neoorigins.api.origin.ComponentCodecHelper.CODEC
                .parse(com.mojang.serialization.JsonOps.INSTANCE, body.get(field))
                .result().map(net.minecraft.network.chat.Component::getString)
                .orElseGet(() -> readText(body, field, fallback));
        } catch (RuntimeException e) {
            return readText(body, field, fallback);
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
     *  so the cloned body matches what the Powers tab would save, and point its
     *  references at the cloned ids. Display fields stay. */
    private static String stripDisplayFields(String rawJson, java.util.Map<String, String> renamed) {
        try {
            JsonObject o = JsonParser.parseString(rawJson).getAsJsonObject();
            o.remove("id");
            o.remove("_power_id");
            return PowerIdRemap.rewrite(o, renamed).toString();
        } catch (RuntimeException e) {
            return rawJson;
        }
    }
}
