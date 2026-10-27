package com.cyberday1.neoorigins.compat.action;

import com.google.gson.JsonParser;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The item-side {@code neoorigins:and} takes a single item-action object for
 * {@code actions} as well as an array, matching the entity-side combinator.
 * A bare object used to make the whole action a silent no-op.
 */
class ItemAndSingleObjectTest {

    private static ItemStack stack() {
        ItemStack s = mock(ItemStack.class);
        when(s.isEmpty()).thenReturn(false);
        return s;
    }

    @Test
    void aSingleObjectRuns() {
        ItemAction action = ItemActionParser.parse(JsonParser.parseString(
            "{ \"type\": \"origins:and\", \"actions\": { \"type\": \"origins:consume\", \"amount\": 2 } }")
            .getAsJsonObject());
        ItemStack s = stack();
        action.execute(s);
        verify(s, times(1)).shrink(2);
    }

    @Test
    void anArrayStillRunsEveryEntry() {
        ItemAction action = ItemActionParser.parse(JsonParser.parseString("""
            { "type": "origins:and", "actions": [
              { "type": "origins:consume", "amount": 1 },
              { "type": "origins:consume", "amount": 3 } ] }
            """).getAsJsonObject());
        ItemStack s = stack();
        action.execute(s);
        verify(s, times(1)).shrink(1);
        verify(s, times(1)).shrink(3);
    }
}
