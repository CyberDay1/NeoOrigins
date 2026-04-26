package net.minecraft.client.gui.layouts;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CommonLayouts {
    private static final int LABEL_SPACING = 4;

    private CommonLayouts() {
    }

    public static Layout labeledElement(Font p_300005_, LayoutElement p_299827_, Component p_299870_) {
        return labeledElement(p_300005_, p_299827_, p_299870_, p_300036_ -> {
        });
    }

    public static Layout labeledElement(Font p_300013_, LayoutElement p_299865_, Component p_300008_, Consumer<LayoutSettings> p_299888_) {
        LinearLayout linearlayout = LinearLayout.vertical().spacing(4);
        linearlayout.addChild(new StringWidget(p_300008_, p_300013_));
        linearlayout.addChild(p_299865_, p_299888_);
        return linearlayout;
    }
}
