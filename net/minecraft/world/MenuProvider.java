package net.minecraft.world;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuConstructor;

public interface MenuProvider extends MenuConstructor, net.neoforged.neoforge.client.extensions.IMenuProviderExtension {
    Component getDisplayName();
}
