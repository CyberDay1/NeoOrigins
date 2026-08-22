package com.cyberday1.neoorigins.power.builtin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for the tester report "shield USE is not blocked" against the
 * modular {@code neoorigins:restrict_items} gate. Bootstraps Minecraft so the real
 * {@code item_condition} predicate resolves {@code minecraft:shield} against a live
 * {@link Items#SHIELD} stack — no in-world launch required.
 *
 * <p>Decodes the EXACT documented "fragile" example through the real
 * {@link RestrictItemsPower.Config#CODEC} and asserts the shared decision
 * functions forbid a shield in every hand / null-hand combination.
 *
 * <p><b>Why this differs from the 1.21.1 copy.</b> 26.x moved item default
 * components out of registration into a datapack-reload step, so a stack cannot be
 * constructed at all while its item's components are unbound. Binding EMPTY is safe
 * here because the gate matches on item identity alone — nothing below reads a
 * default component.
 */
class RestrictItemsShieldGateTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (Item item : new Item[] { Items.SHIELD, Items.TOTEM_OF_UNDYING, Items.WOODEN_SWORD }) {
            item.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        }
    }

    // The exact docs/POWER_TYPES.md "fragile" example (~line 2171).
    private static final String FRAGILE_JSON = """
        {
          "type": "neoorigins:restrict_items",
          "item_condition": {
            "type": "neoorigins:or",
            "conditions": [
              { "item": "minecraft:shield" },
              { "item": "minecraft:totem_of_undying" }
            ]
          },
          "prevent_use": true,
          "deny": true,
          "name": "Fragile",
          "description": "Cannot raise a shield, and a totem of undying will not save you."
        }
        """;

    private static RestrictItemsPower.Config decodeFragile() {
        JsonObject json = JsonParser.parseString(FRAGILE_JSON).getAsJsonObject();
        DataResult<? extends com.mojang.datafixers.util.Pair<RestrictItemsPower.Config, ?>> decoded =
            RestrictItemsPower.Config.CODEC.decode(JsonOps.INSTANCE, json);
        assertTrue(decoded.error().isEmpty(),
            () -> "Config.CODEC.decode failed: " + decoded.error().map(e -> e.message()).orElse("?"));
        return decoded.result().get().getFirst();
    }

    @Test
    void fragileGateForbidsShieldUse() {
        RestrictItemsPower.Config cfg = decodeFragile();
        assertTrue(cfg.preventUse(), "prevent_use should decode true");
        assertTrue(cfg.deny(), "deny should decode true");

        ItemStack shield = new ItemStack(Items.SHIELD);

        assertTrue(RestrictItemsPower.isForbidden(shield, cfg),
            "shield should match the or(shield,totem) blacklist");
        // null entity → no whole-power condition → conditionPasses true.
        assertTrue(RestrictItemsPower.blocksUse(null, shield, InteractionHand.OFF_HAND, cfg),
            "shield USE in OFF_HAND must be blocked");
        assertTrue(RestrictItemsPower.blocksUse(null, shield, InteractionHand.MAIN_HAND, cfg),
            "shield USE in MAIN_HAND must be blocked");
        assertTrue(RestrictItemsPower.blocksUse(null, shield, null, cfg),
            "shield USE with null (any) hand must be blocked");
    }

    @Test
    void fragileGateForbidsTotemUse() {
        RestrictItemsPower.Config cfg = decodeFragile();
        ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING);
        assertTrue(RestrictItemsPower.blocksUse(null, totem, null, cfg),
            "totem USE must be blocked");
    }

    @Test
    void fragileGateAllowsUnrelatedItem() {
        RestrictItemsPower.Config cfg = decodeFragile();
        ItemStack sword = new ItemStack(Items.WOODEN_SWORD);
        assertFalse(RestrictItemsPower.isForbidden(sword, cfg),
            "a sword must not be caught by the shield/totem blacklist");
        assertFalse(RestrictItemsPower.blocksUse(null, sword, InteractionHand.MAIN_HAND, cfg),
            "sword USE must not be blocked");
    }
}
