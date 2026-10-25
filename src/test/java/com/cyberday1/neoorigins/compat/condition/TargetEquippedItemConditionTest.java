package com.cyberday1.neoorigins.compat.condition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The classic Enderian's Gourd Blindness says "anyone wearing a carved pumpkin is
 * invisible to you", which is a statement about the entity being hidden. Its
 * {@code prevent_entity_render} therefore puts {@code equipped_item} in
 * {@code entity_condition} — and that verb was not entity-general, so the power
 * compiled to NEVER, hid nobody, and warned on every single load. A headless boot
 * caught it.
 *
 * <p>{@code LivingEntity.getItemBySlot} answers for any entity, so the verb does
 * generalize for the vanilla slots.
 */
class TargetEquippedItemConditionTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    /** The condition exactly as classic_enderian_gourd_blindness ships it. */
    private static final String GOURD = """
        {
          "type": "neoorigins:equipped_item",
          "equipment_slot": "head",
          "item_condition": { "item": "minecraft:carved_pumpkin" }
        }
        """;

    /**
     * A stack of the given item. Mocked rather than real: 26.x leaves item components
     * unbound under a bare bootstrap, so {@code stackOf(Items.CARVED_PUMPKIN)}
     * throws there, and the condition only ever asks a stack whether it is empty and
     * whether it is a given item.
     */
    private static ItemStack stackOf(Item item) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.isEmpty()).thenReturn(false);
        when(stack.is(any(Item.class))).thenAnswer(inv -> inv.getArgument(0) == item);
        return stack;
    }

    private static LivingEntity wearing(EquipmentSlot slot, ItemStack stack) {
        LivingEntity target = mock(LivingEntity.class);
        for (EquipmentSlot s : EquipmentSlot.values()) {
            when(target.getItemBySlot(s)).thenReturn(ItemStack.EMPTY);
        }
        when(target.getItemBySlot(slot)).thenReturn(stack);
        return target;
    }

    @Test
    void aPumpkinWearerMatchesAndEveryoneElseDoesNot() {
        TargetCondition cond = TargetConditionParser.parse(obj(GOURD), "neoorigins:classic_enderian_gourd_blindness");
        assertNotNull(cond, "equipped_item must compile as a target condition, not drop out of the switch");

        LivingEntity pumpkin = wearing(EquipmentSlot.HEAD, stackOf(Items.CARVED_PUMPKIN));
        LivingEntity bareHead = wearing(EquipmentSlot.HEAD, ItemStack.EMPTY);
        LivingEntity helmet = wearing(EquipmentSlot.HEAD, stackOf(Items.IRON_HELMET));
        LivingEntity pumpkinInHand = wearing(EquipmentSlot.MAINHAND, stackOf(Items.CARVED_PUMPKIN));

        assertTrue(cond.test(pumpkin, null), "a pumpkin-wearing entity must match");
        assertFalse(cond.test(bareHead, null), "an entity wearing nothing must not match");
        assertFalse(cond.test(helmet, null), "a different helmet must not match");
        assertFalse(cond.test(pumpkinInHand, null), "the slot is read, not the whole inventory");
    }

    /** No item_condition is Apoli's slot-presence check. */
    @Test
    void aSlotWithNoItemConditionIsAPresenceCheck() {
        TargetCondition cond = TargetConditionParser.parse(
            obj("{ \"type\": \"apoli:equipped_item\", \"equipment_slot\": \"chest\" }"), "test:presence");
        assertNotNull(cond);
        assertTrue(cond.test(wearing(EquipmentSlot.CHEST, stackOf(Items.ELYTRA)), null));
        assertFalse(cond.test(wearing(EquipmentSlot.CHEST, ItemStack.EMPTY), null));
    }

    /**
     * Curios/Accessories go through a player-typed inspector, so the accessory slot has
     * to stay non-generalizable rather than quietly answer for a mob.
     */
    @Test
    void theAccessorySlotIsNotGeneralizable() {
        assertNull(TargetConditionParser.parse(
            obj("{ \"type\": \"neoorigins:equipped_item\", \"equipment_slot\": \"accessory\" }"), "test:acc"));
    }

    /**
     * This parser fails closed. An item verb the package does not implement parses as
     * always-true, which inside a prevention would hide every entity instead of none, so
     * it must refuse to compile at all.
     */
    @Test
    void anUnsupportedItemVerbRefusesInsteadOfMatchingEverything() {
        assertNull(TargetConditionParser.parse(obj("""
            {
              "type": "neoorigins:equipped_item",
              "equipment_slot": "head",
              "item_condition": { "type": "apoli:fabric_only_nonsense", "value": true }
            }
            """), "test:unsupported"));

        // ... including one buried in a combinator.
        assertNull(TargetConditionParser.parse(obj("""
            {
              "type": "neoorigins:equipped_item",
              "equipment_slot": "head",
              "item_condition": {
                "type": "apoli:and",
                "conditions": [
                  { "item": "minecraft:carved_pumpkin" },
                  { "type": "apoli:fabric_only_nonsense" }
                ]
              }
            }
            """), "test:unsupported_nested"));

        // A combinator whose children are all understood still compiles.
        assertNotNull(TargetConditionParser.parse(obj("""
            {
              "type": "neoorigins:equipped_item",
              "equipment_slot": "head",
              "item_condition": {
                "type": "apoli:or",
                "conditions": [
                  { "item": "minecraft:carved_pumpkin" },
                  { "item": "minecraft:jack_o_lantern" }
                ]
              }
            }
            """), "test:supported_nested"));
    }
}
