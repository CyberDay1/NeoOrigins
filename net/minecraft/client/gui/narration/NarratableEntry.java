package net.minecraft.client.gui.narration;

import net.minecraft.client.gui.components.TabOrderedElement;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface NarratableEntry extends TabOrderedElement, NarrationSupplier {
    NarratableEntry.NarrationPriority narrationPriority();

    default boolean isActive() {
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    public static enum NarrationPriority {
        NONE,
        HOVERED,
        FOCUSED;

        public boolean isTerminal() {
            return this == FOCUSED;
        }
    }
}
