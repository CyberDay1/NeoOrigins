package com.cyberday1.neoorigins.compat.condition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The player-side {@code equipped_item} condition takes the full item-condition
 * vocabulary for {@code item_condition}, and leaving it out is a presence check,
 * as on 1.21.1. It used to read only id / tag / empty / ingredient and answer
 * every other shape, and an omitted item_condition, with true.
 */
class EquippedItemItemConditionTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    /** Mocked: 26.x leaves item components unbound under a bare bootstrap. */
    private static ItemStack stackOf(Item item) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.isEmpty()).thenReturn(false);
        when(stack.is(any(Item.class))).thenAnswer(inv -> inv.getArgument(0) == item);
        return stack;
    }

    private static ServerPlayer wearing(EquipmentSlot slot, ItemStack stack) {
        ServerPlayer player = mock(ServerPlayer.class);
        for (EquipmentSlot s : EquipmentSlot.values()) {
            when(player.getItemBySlot(s)).thenReturn(ItemStack.EMPTY);
        }
        when(player.getItemBySlot(slot)).thenReturn(stack);
        return player;
    }

    @Test
    void anOmittedItemConditionIsAPresenceCheck() {
        EntityCondition cond = ConditionParser.parseEquippedItem(
            obj("{ \"type\": \"origins:equipped_item\", \"equipment_slot\": \"head\" }"), "test:presence");
        assertTrue(cond.test(wearing(EquipmentSlot.HEAD, stackOf(Items.CARVED_PUMPKIN))));
        assertFalse(cond.test(wearing(EquipmentSlot.HEAD, ItemStack.EMPTY)),
            "an empty slot must not satisfy a presence check");
    }

    @Test
    void combinatorsReachTheSharedItemConditionParser() {
        EntityCondition notPumpkin = ConditionParser.parseEquippedItem(obj("""
            {
              "type": "origins:equipped_item",
              "equipment_slot": "head",
              "item_condition": {
                "type": "origins:and",
                "conditions": [
                  { "type": "origins:empty", "inverted": true },
                  { "type": "origins:not", "condition": { "item": "minecraft:carved_pumpkin" } }
                ]
              }
            }
            """), "test:and");
        assertTrue(notPumpkin.test(wearing(EquipmentSlot.HEAD, stackOf(Items.IRON_HELMET))));
        assertFalse(notPumpkin.test(wearing(EquipmentSlot.HEAD, stackOf(Items.CARVED_PUMPKIN))));
        assertFalse(notPumpkin.test(wearing(EquipmentSlot.HEAD, ItemStack.EMPTY)));
    }

    @Test
    void theShorthandFormsStillMatch() {
        EntityCondition pumpkin = ConditionParser.parseEquippedItem(obj("""
            { "type": "origins:equipped_item", "equipment_slot": "head",
              "item_condition": { "id": "minecraft:carved_pumpkin" } }
            """), "test:id");
        assertTrue(pumpkin.test(wearing(EquipmentSlot.HEAD, stackOf(Items.CARVED_PUMPKIN))));
        assertFalse(pumpkin.test(wearing(EquipmentSlot.HEAD, stackOf(Items.IRON_HELMET))));

        EntityCondition empty = ConditionParser.parseEquippedItem(obj("""
            { "type": "origins:equipped_item", "equipment_slot": "offhand",
              "item_condition": { "type": "origins:empty" } }
            """), "test:empty");
        assertTrue(empty.test(wearing(EquipmentSlot.HEAD, stackOf(Items.CARVED_PUMPKIN))));
        assertFalse(empty.test(wearing(EquipmentSlot.OFFHAND, stackOf(Items.SHIELD))));
    }
}
