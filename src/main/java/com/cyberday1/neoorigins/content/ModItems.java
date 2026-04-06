package com.cyberday1.neoorigins.content;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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

    private static final ResourceKey<Item> ORB_KEY =
        ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, "orb_of_origin"));

    public static final DeferredHolder<Item, OrbOfOriginItem> ORB_OF_ORIGIN =
        ITEMS.register("orb_of_origin", () -> new OrbOfOriginItem(
            new Item.Properties().setId(ORB_KEY).stacksTo(1).rarity(Rarity.RARE)));

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(ModItems::addToCreativeTab);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(new ItemStack(ORB_OF_ORIGIN.get()));
        }
    }
}
