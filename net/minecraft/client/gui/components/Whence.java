package net.minecraft.client.gui.components;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum Whence {
    ABSOLUTE,
    RELATIVE,
    END;
}
