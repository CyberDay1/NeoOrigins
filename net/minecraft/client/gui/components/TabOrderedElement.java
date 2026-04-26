package net.minecraft.client.gui.components;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface TabOrderedElement {
    default int getTabOrderGroup() {
        return 0;
    }
}
