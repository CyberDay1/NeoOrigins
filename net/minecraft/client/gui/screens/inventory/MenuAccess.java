package net.minecraft.client.gui.screens.inventory;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface MenuAccess<T extends AbstractContainerMenu> {
    T getMenu();
}
