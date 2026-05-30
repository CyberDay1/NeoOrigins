package com.cyberday1.neoorigins.compat.registry;

import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Registry descriptor for one player-targeted condition verb. Condition analogue
 * of {@link ActionType} — see that type for the keystone rationale (including the
 * alias-set mechanism for multi-label switch arms).
 *
 * @param id      canonical {@code neoorigins:<verb>} id (also the registry key).
 * @param factory builds the compiled {@link EntityCondition} from the verb's JSON.
 * @param fields  declared config fields, in author-facing order.
 * @param aliases additional {@code neoorigins:<verb>} ids that dispatch to the
 *                same factory (e.g. {@code resource_level} → {@code resource}).
 *                These are <em>known verbs</em>, not separate registry types: only
 *                the canonical {@link #id()} is registered and counted toward the
 *                type total; the {@code SchemaFormCheck} audit treats the alias set
 *                as known so {@code KNOWN_TYPES} parity holds without the alias being
 *                a distinct descriptor. Mirrors {@link ActionType}.
 */
public record ConditionType(Identifier id, Factory factory, List<FieldSpec> fields,
                            List<Identifier> aliases) {

    public ConditionType {
        fields = List.copyOf(fields);
        aliases = List.copyOf(aliases);
    }

    /** Convenience: a descriptor with no aliases (the common case). */
    public ConditionType(Identifier id, Factory factory, List<FieldSpec> fields) {
        this(id, factory, fields, List.of());
    }

    /** Parse lambda: {@code (json, contextId) -> EntityCondition} — mirrors {@code ConditionParser.parse}. */
    @FunctionalInterface
    public interface Factory {
        EntityCondition create(JsonObject json, String contextId);
    }
}
