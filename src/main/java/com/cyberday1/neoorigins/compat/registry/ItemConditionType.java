package com.cyberday1.neoorigins.compat.registry;

import com.cyberday1.neoorigins.compat.condition.ItemCondition;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Registry descriptor for one ItemStack-targeted condition verb. Item analogue
 * of {@link ConditionType}. The {@link Factory} takes no {@code contextId} —
 * {@code ItemConditionParser.parse} operates on the stack alone.
 *
 * @param id      canonical {@code neoorigins:<verb>} id (also the registry key).
 * @param factory builds the compiled {@link ItemCondition} from the verb's JSON.
 * @param fields  declared config fields, in author-facing order.
 */
public record ItemConditionType(ResourceLocation id, Factory factory, List<FieldSpec> fields) {

    public ItemConditionType {
        fields = List.copyOf(fields);
    }

    /** Parse lambda: {@code json -> ItemCondition} — mirrors {@code ItemConditionParser.parse}. */
    @FunctionalInterface
    public interface Factory {
        ItemCondition create(JsonObject json);
    }
}
