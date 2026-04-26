package net.minecraft.client.gui.navigation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CommonInputs {
    public static boolean selected(int p_279282_) {
        return p_279282_ == 257 || p_279282_ == 32 || p_279282_ == 335;
    }
}
