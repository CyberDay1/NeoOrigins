package com.cyberday1.neoorigins.compat.registry;

import com.cyberday1.neoorigins.compat.action.EntityAction;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Registry descriptor for one player-targeted action verb.
 *
 * <p>The keystone of the registry refactor (Phase 1): {@code (id, factory,
 * fields)} is the single source for parsing/validation, JSON schema, doc tables,
 * and editor forms. The {@link Factory} is the verb's parse lambda
 * (lift-and-shift of the current {@code ActionParser} switch arm); the
 * {@link FieldSpec} list is transcribed from its hand-written schema branch.
 *
 * <p>Behavior-neutral until {@code ActionParser.parse} is switched over to a
 * registry lookup (later migration step) — registering descriptors changes
 * nothing on its own.
 *
 * @param id      canonical {@code neoorigins:<verb>} id (also the registry key).
 * @param factory builds the compiled {@link EntityAction} from the verb's JSON.
 * @param fields  declared config fields, in author-facing order.
 */
public record ActionType(ResourceLocation id, Factory factory, List<FieldSpec> fields) {

    public ActionType {
        fields = List.copyOf(fields);
    }

    /** Parse lambda: {@code (json, contextId) -> EntityAction} — mirrors {@code ActionParser.parse}. */
    @FunctionalInterface
    public interface Factory {
        EntityAction create(JsonObject json, String contextId);
    }
}
