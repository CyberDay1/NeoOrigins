package com.cyberday1.neoorigins.compat.registry;

import com.cyberday1.neoorigins.compat.action.EntityAction;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

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
 * @param aliases additional {@code neoorigins:<verb>} ids that dispatch to the
 *                same factory (e.g. {@code modify_resource} → {@code change_resource}).
 *                These are <em>known verbs</em>, not separate registry types: only
 *                the canonical {@link #id()} is registered and counted toward the
 *                type total; the {@code SchemaFormCheck} audit treats the alias set
 *                as known so {@code KNOWN_TYPES} parity holds without the alias being
 *                a distinct descriptor. See locked decision (Task 1 / alias-sets).
 */
public record ActionType(Identifier id, Factory factory, List<FieldSpec> fields,
                         List<Identifier> aliases) {

    public ActionType {
        fields = List.copyOf(fields);
        aliases = List.copyOf(aliases);
    }

    /** Convenience: a descriptor with no aliases (the common case). */
    public ActionType(Identifier id, Factory factory, List<FieldSpec> fields) {
        this(id, factory, fields, List.of());
    }

    /** Parse lambda: {@code (json, contextId) -> EntityAction} — mirrors {@code ActionParser.parse}. */
    @FunctionalInterface
    public interface Factory {
        EntityAction create(JsonObject json, String contextId);
    }
}
