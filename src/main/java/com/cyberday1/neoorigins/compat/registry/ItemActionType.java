package com.cyberday1.neoorigins.compat.registry;

import com.cyberday1.neoorigins.compat.action.ItemAction;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Registry descriptor for one ItemStack-targeted action verb. Item analogue of
 * {@link ActionType}. Note the {@link Factory} takes no {@code contextId} —
 * {@code ItemActionParser.parse} operates on the stack alone.
 *
 * @param id      canonical {@code neoorigins:<verb>} id (also the registry key).
 * @param factory builds the compiled {@link ItemAction} from the verb's JSON.
 * @param fields  declared config fields, in author-facing order.
 */
public record ItemActionType(ResourceLocation id, Factory factory, List<FieldSpec> fields) {

    public ItemActionType {
        fields = List.copyOf(fields);
    }

    /** Parse lambda: {@code json -> ItemAction} — mirrors {@code ItemActionParser.parse}. */
    @FunctionalInterface
    public interface Factory {
        ItemAction create(JsonObject json);
    }
}
