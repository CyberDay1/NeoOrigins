package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.mob_origin.MobOrigin;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.cyberday1.neoorigins.screen.mobcreator.model.MobOriginDraft;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Server-side gate run before {@link CustomPackWriter#write(net.minecraft.server.MinecraftServer,
 * MobOriginDraft)} — the mob-side analogue of {@link CreatorValidator}. Same
 * strict id-path grammar (C1 path-traversal defense), plus: a target must be
 * set, every power must have a type and well-formed body, and the assembled
 * origin must parse through {@code MobOrigin.CODEC} with the synthetic id
 * injected exactly as {@code MobOriginDataManager} does.
 */
public final class MobCreatorValidator {

    private static final Pattern SAFE_ID_PATH = Pattern.compile("[a-z0-9_-]+(?:/[a-z0-9_-]+)*");

    private MobCreatorValidator() {}

    public record Result(boolean ok, List<String> errors) {
        public String message() { return String.join("; ", errors); }
    }

    public static Result validate(MobOriginDraft draft) {
        List<String> errors = new ArrayList<>();

        Identifier originId;
        try {
            originId = draft.originId();
        } catch (RuntimeException e) {
            return new Result(false, List.of("id path \"" + draft.idPath + "\" is not valid"));
        }
        if (!SAFE_ID_PATH.matcher(originId.getPath()).matches()) {
            return new Result(false, List.of(
                "id path \"" + draft.idPath + "\" is not allowed "
                + "(lowercase a-z, 0-9, _, - and single / only — no '.', no '..')"));
        }
        for (OriginDraft.PowerDraft p : draft.powers) {
            if (p.powerId == null || !SAFE_ID_PATH.matcher(p.powerId.getPath()).matches()) {
                return new Result(false, List.of(
                    "power id \"" + (p.powerId == null ? "(none)" : p.powerId) + "\" is not allowed"));
            }
            if (p.typeId == null || p.typeId.isBlank()) {
                errors.add("a power has no type set");
            }
        }

        boolean hasTarget = (draft.targetEntityType != null && !draft.targetEntityType.isBlank())
            || (draft.targetEntityTag != null && !draft.targetEntityTag.isBlank())
            || !draft.targetEntityTypes.isEmpty();
        if (!hasTarget) {
            errors.add("no target set — set a target entity or a target tag");
        }

        // Assembled mob origin must parse through MobOrigin.CODEC (id injected
        // as MobOriginDataManager#apply does).
        JsonObject json = MobCustomPackSerializer.mobOriginJson(draft);
        json.addProperty("id", originId.toString());
        MobOrigin.CODEC.parse(JsonOps.INSTANCE, json).error().ifPresent(err ->
            errors.add("mob origin JSON invalid: " + err.message()));

        return new Result(errors.isEmpty(), errors);
    }
}
