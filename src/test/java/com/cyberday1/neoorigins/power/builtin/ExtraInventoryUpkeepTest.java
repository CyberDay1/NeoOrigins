package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.rig.InMemoryConfig;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * extra_inventory's optional cost: charged while its own menu is open, and the
 * menu closes, keeping its items, at the first charge the player can't pay.
 */
class ExtraInventoryUpkeepTest {

    private static final Identifier ID = Identifier.parse("neoorigins:test_pocket");
    private static final Identifier OTHER = Identifier.parse("neoorigins:test_other_pocket");

    private static boolean loadedToggles;

    @BeforeAll
    static void loadConfig() {
        loadedToggles = InMemoryConfig.loadIfAbsent(ContentTogglesConfig.SPEC);
        // The harness does not bind item components; a stack needs a max size.
        for (var item : java.util.List.of(Items.DIAMOND, Items.EMERALD)) {
            item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 64)
                .build());
        }
    }

    @AfterAll
    static void unloadConfig() {
        if (loadedToggles) InMemoryConfig.unload(ContentTogglesConfig.SPEC);
    }

    private static ExtraInventoryPower.Config config(String body) {
        return new ExtraInventoryPower().codec()
            .parse(JsonOps.INSTANCE, JsonParser.parseString(body)).getOrThrow();
    }

    private static ServerPlayer player(int food) {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        sp.connection = Mockito.mock(ServerGamePacketListenerImpl.class);
        sp.getFoodData().setFoodLevel(food);
        sp.getFoodData().setSaturation(0f);
        return sp;
    }

    /** Puts the player in an extra-inventory menu opened by {@code opener}. */
    private static SimpleContainer open(ServerPlayer sp, Identifier opener) {
        SimpleContainer container = new SimpleContainer(9);
        sp.containerMenu = new ExtraInventoryPower.ExtraInventoryMenu(
            MenuType.GENERIC_9x1, 1, sp.getInventory(), container, 1, opener);
        return container;
    }

    private static void tickTo(ServerPlayer sp, PowerHolder<ExtraInventoryPower.Config> holder, int from, int to) {
        for (int t = from; t <= to; t++) {
            sp.tickCount = t;
            holder.onTick(sp);
        }
    }

    @Test
    void costFieldsAreOptional() {
        var c = config("{}");
        assertTrue(c.upkeep().isFree());
        assertEquals(20, c.upkeepInterval());
        var paid = config("{\"hunger_cost\": 2, \"upkeep_interval\": 0}");
        assertEquals(2, paid.upkeep().hungerCost());
        assertEquals(1, paid.upkeep().interval(), "the interval is clamped to at least 1");
    }

    @Test
    void chargesOnTheBeatWhileItsMenuIsOpen() {
        ServerPlayer sp = player(20);
        var holder = new PowerHolder<>(ID, new ExtraInventoryPower(),
            config("{\"hunger_cost\": 2, \"upkeep_interval\": 20}"), Component.empty(), Component.empty());
        open(sp, ID);

        tickTo(sp, holder, 1, 40);

        assertEquals(16, sp.getFoodData().getFoodLevel(), "charged on ticks 20 and 40 only");
        assertTrue(sp.containerMenu instanceof ExtraInventoryPower.ExtraInventoryMenu);
    }

    @Test
    void chargesNothingWhileClosedOrWhenAnotherPowerOpenedTheMenu() {
        ServerPlayer sp = player(20);
        var holder = new PowerHolder<>(ID, new ExtraInventoryPower(),
            config("{\"hunger_cost\": 2}"), Component.empty(), Component.empty());

        tickTo(sp, holder, 1, 40);
        assertEquals(20, sp.getFoodData().getFoodLevel(), "no menu open");

        open(sp, OTHER);
        tickTo(sp, holder, 41, 80);
        assertEquals(20, sp.getFoodData().getFoodLevel(), "another power's menu is not this power's to charge");
    }

    @Test
    void closesWithoutLosingItemsAtTheFirstChargeItCannotPay() {
        ServerPlayer sp = player(3);
        var holder = new PowerHolder<>(ID, new ExtraInventoryPower(),
            config("{\"hunger_cost\": 2}"), Component.empty(), Component.empty());
        SimpleContainer container = open(sp, ID);
        container.setItem(0, new ItemStack(Items.DIAMOND, 5));
        sp.containerMenu.setCarried(new ItemStack(Items.EMERALD, 3));

        tickTo(sp, holder, 1, 20);
        assertTrue(sp.containerMenu instanceof ExtraInventoryPower.ExtraInventoryMenu, "paid the first charge");
        assertEquals(1, sp.getFoodData().getFoodLevel());

        tickTo(sp, holder, 21, 40);

        assertSame(sp.inventoryMenu, sp.containerMenu, "closed at the charge it could not pay");
        assertEquals(1, sp.getFoodData().getFoodLevel(), "nothing deducted from a short player");
        assertEquals(5, container.getItem(0).getCount(), "the stored items stay put");
        assertEquals(Items.DIAMOND, container.getItem(0).getItem());
        assertTrue(sp.getInventory().contains(new ItemStack(Items.EMERALD)),
            "the stack on the cursor goes back to the player's inventory");
    }

    @Test
    void theKeyDoesNothingForAPlayerWhoCannotPayOneCharge() {
        ServerPlayer sp = player(1);
        var holder = new PowerHolder<>(ID, new ExtraInventoryPower(),
            config("{\"hunger_cost\": 2}"), Component.empty(), Component.empty());

        holder.onActivated(sp);

        assertFalse(sp.containerMenu instanceof ExtraInventoryPower.ExtraInventoryMenu);
    }
}
