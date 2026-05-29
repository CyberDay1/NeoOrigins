package com.cyberday1.neoorigins.compat.registry;

import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Registry descriptor for one player-targeted condition verb. Condition analogue
 * of {@link ActionType} — see that type for the keystone rationale.
 *
 * @param id      canonical {@code neoorigins:<verb>} id (also the registry key).
 * @param factory builds the compiled {@link EntityCondition} from the verb's JSON.
 * @param fields  declared config fields, in author-facing order.
 */
public record ConditionType(ResourceLocation id, Factory factory, List<FieldSpec> fields) {

    public ConditionType {
        fields = List.copyOf(fields);
    }

    /** Parse lambda: {@code (json, contextId) -> EntityCondition} — mirrors {@code ConditionParser.parse}. */
    @FunctionalInterface
    public interface Factory {
        EntityCondition create(JsonObject json, String contextId);
    }
}
