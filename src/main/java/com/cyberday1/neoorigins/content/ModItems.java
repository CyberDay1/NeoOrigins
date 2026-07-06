package com.cyberday1.neoorigins.content;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(Registries.ITEM, NeoOrigins.MOD_ID);

    public static final DeferredHolder<Item, OrbOfOriginItem> ORB_OF_ORIGIN =
        ITEMS.register("orb_of_origin", () -> new OrbOfOriginItem(
            new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // Resets only the class layer, then reopens the picker scoped to it.
    public static final DeferredHolder<Item, OrbOfClassItem> ORB_OF_CLASS =
        ITEMS.register("orb_of_class", () -> new OrbOfClassItem(
            new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // Fully inert orbs: right-click does nothing; datapacks bind behaviour via
    // action_on_item_use / action_on_event powers matching #neoorigins:orbs.
    public static final DeferredHolder<Item, Item> GOLD_ORB =
        ITEMS.register("gold_orb", () -> new Item(
            new Item.Properties().stacksTo(64).rarity(Rarity.RARE)));

    public static final DeferredHolder<Item, Item> PINK_ORB =
        ITEMS.register("pink_orb", () -> new Item(
            new Item.Properties().stacksTo(64).rarity(Rarity.RARE)));

    public static final DeferredHolder<Item, Item> PURPLE_ORB =
        ITEMS.register("purple_orb", () -> new Item(
            new Item.Properties().stacksTo(64).rarity(Rarity.RARE)));

    public static final DeferredHolder<Item, Item> TEAL_ORB =
        ITEMS.register("teal_orb", () -> new Item(
            new Item.Properties().stacksTo(64).rarity(Rarity.RARE)));

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(ModItems::addToCreativeTab);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(new ItemStack(ORB_OF_ORIGIN.get()));
            event.accept(new ItemStack(ORB_OF_CLASS.get()));
            event.accept(new ItemStack(GOLD_ORB.get()));
            event.accept(new ItemStack(PINK_ORB.get()));
            event.accept(new ItemStack(PURPLE_ORB.get()));
            event.accept(new ItemStack(TEAL_ORB.get()));
        }
    }
}
