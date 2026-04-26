package net.minecraft.client.gui.narration;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum NarratedElementType {
    TITLE,
    POSITION,
    HINT,
    USAGE;
}
