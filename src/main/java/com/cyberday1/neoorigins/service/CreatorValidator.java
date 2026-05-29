package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

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

    /**
     * Conservative datapack-path grammar: one or more {@code [a-z0-9_-]}
     * segments joined by single {@code /}. Deliberately stricter than
     * {@link ResourceLocation}'s own path validation, which permits {@code .}
     * (and therefore {@code ..}) and odd slashes. {@link CustomPackWriter}
     * resolves these id paths into real filesystem paths, so anything that
     * isn't this grammar (a {@code .}, {@code ..}, leading/trailing/double
     * slash, empty segment) is rejected here before a single file is written.
     */
    private static final Pattern SAFE_ID_PATH = Pattern.compile("[a-z0-9_-]+(?:/[a-z0-9_-]+)*");

    private CreatorValidator() {}

    public record Result(boolean ok, List<String> errors) {
        public String message() { return String.join("; ", errors); }
    }

    public static Result validate(RegistryAccess registries, OriginDraft draft) {
        List<String> errors = new ArrayList<>();

        // 1. id path must form a valid ResourceLocation path.
        ResourceLocation originId;
        try {
            originId = draft.originId(); // neoorigins_custom:<idPath>
        } catch (RuntimeException e) {
            return new Result(false, List.of(
                "id path \"" + draft.idPath + "\" is not valid (use lowercase a-z, 0-9, _, /, -)"));
        }

        // 1b. id paths become filesystem paths in CustomPackWriter — enforce a
        //     strict grammar so a crafted idPath/powerId/layer cannot escape the
        //     pack directory (path traversal). ResourceLocation alone is not
        //     enough: it accepts '.' and '..'.
        if (!SAFE_ID_PATH.matcher(originId.getPath()).matches()) {
            return new Result(false, List.of(
                "id path \"" + draft.idPath + "\" is not allowed "
                + "(lowercase a-z, 0-9, _, - and single / only — no '.', no '..')"));
        }
        if (draft.layerId != null
                && !SAFE_ID_PATH.matcher(draft.layerId.getPath()).matches()) {
            return new Result(false, List.of(
                "target layer path \"" + draft.layerId + "\" is not allowed"));
        }
        for (OriginDraft.PowerDraft p : draft.powers) {
            if (p.powerId == null
                    || !SAFE_ID_PATH.matcher(p.powerId.getPath()).matches()) {
                return new Result(false, List.of(
                    "power id \"" + (p.powerId == null ? "(none)" : p.powerId)
                    + "\" is not allowed (lowercase a-z, 0-9, _, - and single / only)"));
            }
        }

        // 2. collision with a shipped built-in origin (neoorigins:<idPath>).
        //    Skipped when the draft itself targets the neoorigins namespace —
        //    that is the "load a template and Save to override the vanilla
        //    origin" path, where the collision is the entire point.
        if (!"neoorigins".equals(originId.getNamespace())) {
            try {
                ResourceLocation builtin =
                    ResourceLocation.fromNamespaceAndPath("neoorigins", originId.getPath());
                if (OriginDataManager.INSTANCE.getOrigin(builtin) != null) {
                    errors.add("id collides with built-in origin " + builtin
                        + " — pick a different id path");
                }
            } catch (RuntimeException ignored) { /* path already validated above */ }
        }

        // 3. assembled origin parses through Origin.CODEC (id injected as the
        //    data manager does at OriginDataManager#apply).
        JsonObject originJson = CustomPackSerializer.originJson(draft);
        originJson.addProperty("id", originId.toString());
        Origin.CODEC.parse(JsonOps.INSTANCE, originJson).error().ifPresent(err ->
            errors.add("origin JSON invalid: " + err.message()));

        // 4. every power: registered type, real codec parse, required fields,
        //    and unknown registry/condition/action ids (shared with the client
        //    problems panel so both report identically).
        errors.addAll(DraftSanity.powerProblems(registries, draft));

        // 5. target layer exists, or is an auto-merge origin/class path.
        ResourceLocation layer = draft.layerId;
        if (layer == null) {
            errors.add("no target layer set");
        } else if (LayerDataManager.INSTANCE.getLayer(layer) == null
                && !AUTO_MERGE_PATHS.contains(layer.getPath())) {
            errors.add("target layer " + layer + " does not exist");
        }

        return new Result(errors.isEmpty(), errors);
    }
}
