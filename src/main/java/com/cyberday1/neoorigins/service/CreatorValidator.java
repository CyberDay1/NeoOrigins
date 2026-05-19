package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.power.registry.PowerTypes;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft.PowerDraft;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Server-side gate run before {@link CustomPackWriter#write} so a malformed
 * draft is rejected with a readable reason instead of writing files that fail
 * silently on reload. Server is authoritative (it writes a shared datapack);
 * the client UI is convenience only.
 *
 * <p>Checks: a usable id path; no collision with a built-in {@code neoorigins:}
 * origin (re-saving over a previous {@code neoorigins_custom:} id is fine —
 * that's editing your own); the assembled origin parses through
 * {@link Origin#CODEC} with the synthetic id injected exactly as
 * {@code OriginDataManager} does; every power's type is registered and its
 * body is well-formed JSON; and the target layer exists (or is an auto-merge
 * {@code origin}/{@code class} path that resolves to the canonical picker).
 */
public final class CreatorValidator {

    /** Layer paths that always resolve via LayerDataManager's auto-merge. */
    private static final Set<String> AUTO_MERGE_PATHS = Set.of("origin", "class");

    private CreatorValidator() {}

    public record Result(boolean ok, List<String> errors) {
        public String message() { return String.join("; ", errors); }
    }

    public static Result validate(OriginDraft draft) {
        List<String> errors = new ArrayList<>();

        // 1. id path must form a valid Identifier path.
        Identifier originId;
        try {
            originId = draft.originId(); // neoorigins_custom:<idPath>
        } catch (RuntimeException e) {
            return new Result(false, List.of(
                "id path \"" + draft.idPath + "\" is not valid (use lowercase a-z, 0-9, _, /, -)"));
        }

        // 2. collision with a shipped built-in origin (neoorigins:<idPath>).
        try {
            Identifier builtin =
                Identifier.fromNamespaceAndPath("neoorigins", originId.getPath());
            if (OriginDataManager.INSTANCE.getOrigin(builtin) != null) {
                errors.add("id collides with built-in origin " + builtin
                    + " — pick a different id path");
            }
        } catch (RuntimeException ignored) { /* path already validated above */ }

        // 3. assembled origin parses through Origin.CODEC (id injected as the
        //    data manager does at OriginDataManager#apply).
        JsonObject originJson = CustomPackSerializer.originJson(draft);
        originJson.addProperty("id", originId.toString());
        Origin.CODEC.parse(JsonOps.INSTANCE, originJson).error().ifPresent(err ->
            errors.add("origin JSON invalid: " + err.message()));

        // 4. every power: registered type + well-formed body.
        int i = 0;
        for (PowerDraft p : draft.powers) {
            i++;
            Identifier type;
            try {
                type = Identifier.parse(p.typeId);
            } catch (RuntimeException e) {
                errors.add("power #" + i + ": invalid type id \"" + p.typeId + "\"");
                continue;
            }
            if (PowerTypes.get(type) == null) {
                errors.add("power #" + i + ": unknown power type " + type);
            }
            try {
                JsonParser.parseString(p.rawJson == null || p.rawJson.isBlank()
                    ? "{}" : p.rawJson);
            } catch (RuntimeException e) {
                errors.add("power #" + i + " (" + type + "): body is not valid JSON");
            }
        }

        // 5. target layer exists, or is an auto-merge origin/class path.
        Identifier layer = draft.layerId;
        if (layer == null) {
            errors.add("no target layer set");
        } else if (LayerDataManager.INSTANCE.getLayer(layer) == null
                && !AUTO_MERGE_PATHS.contains(layer.getPath())) {
            errors.add("target layer " + layer + " does not exist");
        }

        return new Result(errors.isEmpty(), errors);
    }
}
