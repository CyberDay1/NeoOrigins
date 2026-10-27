package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.power.builtin.MoreSmokerXpPower;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.service.EventPowerIndex;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

/**
 * #137: the Cook's food bonuses were lost on shift-click, because vanilla merges the
 * result into the inventory before the crafted/smelted event fires. Each take path
 * here runs the real vanilla menu code, then replays the event with the stack vanilla
 * hands it, and asserts the food in the player's hands carries the bonus exactly once.
 *
 * <p>Why this differs from the 1.21.1 copy: 26.x binds item default components at
 * datapack reload, so a bare bootstrap leaves them unbound. The foods are bound here
 * with their vanilla FOOD values and a stack size of 64, and the inventory filler is
 * bound empty, so it stacks to one.
 */
class CookFoodBoostTakePathsTest {

    private static final Identifier GOOD_MEALS =
        Identifier.fromNamespaceAndPath("neoorigins", "class_cook_food");
    /** Shipped Good Meals: +1 nutrition, +0.5 saturation. */
    private static final int MEALS_NUTRITION = 1;
    private static final float MEALS_SATURATION = 0.5f;
    /** Smoking Expert at its default multiplier 2: +2 nutrition, +0.5 saturation. */
    private static final int EXPERT_NUTRITION = 2;
    private static final float EXPERT_SATURATION = 0.5f;

    private PowerHolder<?> holder;
    private ServerPlayer cook;

    @BeforeAll
    static void load() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        food(Items.BREAD, 5, 6.0f);
        food(Items.COOKIE, 2, 0.4f);
        food(Items.COOKED_BEEF, 8, 12.8f);
        Items.STONE.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        RealDatapack.load();
    }

    private static void food(Item item, int nutrition, float saturation) {
        item.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
            .set(DataComponents.MAX_STACK_SIZE, 64)
            .set(DataComponents.FOOD, new FoodProperties(nutrition, saturation, false))
            .build());
    }

    @AfterEach
    void revoke() {
        if (holder != null && cook != null) PlayerLifecycle.revoke(holder, cook);
        if (cook != null) EventPowerIndex.clearAll(cook.getUUID());
    }

    // ---- crafting -----------------------------------------------------------

    @Test
    void shiftClickCraftIsBoostedOnce() {
        ServerPlayer sp = cook();
        InventoryMenu menu = sp.inventoryMenu;
        ItemStack result = assemble(sp, menu, new ItemStack(Items.BREAD));

        menu.quickMoveStack(sp, 0);
        crafted(sp, result);

        ItemStack moved = findInInventory(sp, Items.BREAD);
        assertFood(moved, base(Items.BREAD), MEALS_NUTRITION, MEALS_SATURATION);
    }

    @Test
    void normalClickCraftIsBoostedOnceNotTwice() {
        ServerPlayer sp = cook();
        InventoryMenu menu = sp.inventoryMenu;
        assemble(sp, menu, new ItemStack(Items.BREAD));

        menu.clicked(0, 0, ContainerInput.PICKUP, sp);
        ItemStack carried = menu.getCarried();
        // The click path fires ItemCraftedEvent with the taken stack: the old boost
        // runs on top of the result-slot hook and must not stack a second bonus.
        crafted(sp, carried);
        crafted(sp, carried);

        assertFood(carried, base(Items.BREAD), MEALS_NUTRITION, MEALS_SATURATION);
    }

    /** Repeated shift-craft (recipe book, multi-craft): every unit boosted once, all in one stack. */
    @Test
    void multiCraftBoostsEachUnitOnceAndTheyStack() {
        ServerPlayer sp = cook();
        InventoryMenu menu = sp.inventoryMenu;
        for (int i = 0; i < 4; i++) {
            ItemStack result = assemble(sp, menu, new ItemStack(Items.COOKIE, 8));
            menu.quickMoveStack(sp, 0);
            crafted(sp, result);
        }

        ItemStack moved = findInInventory(sp, Items.COOKIE);
        assertEquals(32, moved.getCount(), "four boosted crafts must merge into one stack");
        assertFood(moved, base(Items.COOKIE), MEALS_NUTRITION, MEALS_SATURATION);
    }

    /** Control: a non-Cook's food is left as vanilla made it, with no marker. */
    @Test
    void nonCookCraftIsUntouched() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        InventoryMenu menu = sp.inventoryMenu;
        ItemStack result = assemble(sp, menu, new ItemStack(Items.BREAD));
        menu.quickMoveStack(sp, 0);
        crafted(sp, result);

        ItemStack moved = findInInventory(sp, Items.BREAD);
        assertTrue(ItemStack.isSameItemSameComponents(moved, new ItemStack(Items.BREAD)));
    }

    // ---- furnace / smoker ---------------------------------------------------

    @Test
    void furnaceShiftClickIsBoostedOnceWithSmokingExpert() {
        ServerPlayer sp = cook();
        SimpleContainer furnace = new SimpleContainer(3);
        FurnaceMenu menu = openFurnace(sp, furnace);
        ItemStack output = new ItemStack(Items.COOKED_BEEF, 6);
        furnace.setItem(2, output);

        try (MockedStatic<ActiveOriginService> origins = smokingExpert()) {
            menu.quickMoveStack(sp, 2);
            // Replayed twice: the bonus must still land once.
            smelted(sp, output);
            smelted(sp, output);
        }

        ItemStack moved = findInInventory(sp, Items.COOKED_BEEF);
        assertEquals(6, moved.getCount());
        assertFood(moved, base(Items.COOKED_BEEF),
            MEALS_NUTRITION + EXPERT_NUTRITION, MEALS_SATURATION + EXPERT_SATURATION);
    }

    @Test
    void furnaceNormalClickIsBoostedOnce() {
        ServerPlayer sp = cook();
        SimpleContainer furnace = new SimpleContainer(3);
        FurnaceMenu menu = openFurnace(sp, furnace);
        furnace.setItem(2, new ItemStack(Items.COOKED_BEEF, 3));

        try (MockedStatic<ActiveOriginService> origins = smokingExpert()) {
            menu.clicked(2, 0, ContainerInput.PICKUP, sp);
            ItemStack carried = menu.getCarried();
            smelted(sp, carried);
            smelted(sp, carried);
            assertFood(carried, base(Items.COOKED_BEEF),
                MEALS_NUTRITION + EXPERT_NUTRITION, MEALS_SATURATION + EXPERT_SATURATION);
        }
    }

    /**
     * A shift-click with no room for everything leaves the rest in the output slot.
     * That stack must stay vanilla, or the furnace stops stacking new smelts onto it
     * and a hopper pulls boosted food out with no player involved.
     */
    @Test
    void outputLeftInTheFurnaceStaysUnboostedForHoppers() {
        ServerPlayer sp = cook();
        SimpleContainer furnace = new SimpleContainer(3);
        FurnaceMenu menu = openFurnace(sp, furnace);
        ItemStack first = new ItemStack(Items.COOKED_BEEF, 10);
        ItemStack second = new ItemStack(Items.COOKED_BEEF, 64);

        try (MockedStatic<ActiveOriginService> origins = smokingExpert()) {
            furnace.setItem(2, first);
            menu.quickMoveStack(sp, 2);
            smelted(sp, first);
            smelted(sp, first);
            // Everything else full: only the 54 that fit onto the boosted stack can move.
            for (int i = 0; i < 36; i++) {
                if (sp.getInventory().getItem(i).isEmpty()) sp.getInventory().setItem(i, new ItemStack(Items.STONE));
            }
            furnace.setItem(2, second);
            menu.quickMoveStack(sp, 2);
            smelted(sp, second);
            smelted(sp, second);
        }

        ItemStack moved = findInInventory(sp, Items.COOKED_BEEF);
        assertEquals(64, moved.getCount(), "the second batch must top up the boosted stack");
        assertFood(moved, base(Items.COOKED_BEEF),
            MEALS_NUTRITION + EXPERT_NUTRITION, MEALS_SATURATION + EXPERT_SATURATION);

        ItemStack left = furnace.getItem(2);
        assertSame(second, left);
        assertEquals(10, left.getCount());
        assertTrue(ItemStack.isSameItemSameComponents(left, new ItemStack(Items.COOKED_BEEF)),
            "food still in the furnace must keep vanilla components so new smelts stack onto it");
        ItemStack hopper = furnace.removeItem(2, 1);
        assertTrue(ItemStack.isSameItemSameComponents(hopper, new ItemStack(Items.COOKED_BEEF)),
            "a hopper has no player, so its food gets no bonus");
    }

    // ---- harness ------------------------------------------------------------

    private ServerPlayer cook() {
        cook = PlayerLifecycle.realPlayer();
        holder = PowerDataManager.INSTANCE.getPower(GOOD_MEALS);
        assertNotNull(holder, GOOD_MEALS + " did not load");
        PlayerLifecycle.grant(holder, cook);
        return cook;
    }

    /** What CraftingMenu.slotChangedCraftingGrid leaves in the result slot: a fresh stack, hooked. */
    private static ItemStack assemble(ServerPlayer sp, InventoryMenu menu, ItemStack fresh) {
        menu.getSlot(0).set(fresh);
        CraftingPowerEvents.boostCraftedFood(sp, fresh);
        return fresh;
    }

    private static FurnaceMenu openFurnace(ServerPlayer sp, SimpleContainer furnace) {
        FurnaceMenu menu = new FurnaceMenu(1, sp.getInventory(), furnace, new SimpleContainerData(4));
        sp.containerMenu = menu;
        return menu;
    }

    private static void crafted(ServerPlayer sp, ItemStack stack) {
        CraftingPowerEvents.onItemCrafted(new PlayerEvent.ItemCraftedEvent(sp, stack, new SimpleContainer(1)));
    }

    private static void smelted(ServerPlayer sp, ItemStack stack) {
        CraftingPowerEvents.onItemSmelted(new PlayerEvent.ItemSmeltedEvent(sp, stack, Math.max(1, stack.getCount())));
    }

    /** Smoking Expert at its default config; ActiveOriginService otherwise answers nothing. */
    @SuppressWarnings("unchecked")
    private static MockedStatic<ActiveOriginService> smokingExpert() {
        MockedStatic<ActiveOriginService> origins = mockStatic(ActiveOriginService.class);
        origins.when(() -> ActiveOriginService.forEachOfType(any(), eq(MoreSmokerXpPower.class), any()))
            .thenAnswer(inv -> {
                ((Consumer<Object>) inv.getArgument(2)).accept(new MoreSmokerXpPower.Config(2.0f, ""));
                return null;
            });
        return origins;
    }

    private static ItemStack findInInventory(ServerPlayer sp, Item item) {
        ItemStack found = ItemStack.EMPTY;
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack s = sp.getInventory().getItem(i);
            if (!s.is(item)) continue;
            assertTrue(found.isEmpty(), "expected the food in a single stack");
            found = s;
        }
        assertFalse(found.isEmpty(), "the food never reached the inventory");
        return found;
    }

    private static FoodProperties base(Item item) {
        return new ItemStack(item).get(DataComponents.FOOD);
    }

    private static void assertFood(ItemStack stack, FoodProperties base, int nutrition, float saturation) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        assertNotNull(food);
        assertEquals(base.nutrition() + nutrition, food.nutrition(), "nutrition");
        assertEquals(base.saturation() + saturation, food.saturation(), 1e-4, "saturation");
    }
}
