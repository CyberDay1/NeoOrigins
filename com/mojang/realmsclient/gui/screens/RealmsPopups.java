package com.mojang.realmsclient.gui.screens;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.PopupScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RealmsPopups {
    private static final int COLOR_INFO = 8226750;
    private static final Component INFO = Component.translatable("mco.info").withColor(8226750);
    private static final Component WARNING = Component.translatable("mco.warning").withColor(-65536);

    public static PopupScreen infoPopupScreen(Screen p_345107_, Component p_344859_, Consumer<PopupScreen> p_345991_) {
        return new PopupScreen.Builder(p_345107_, INFO)
            .setMessage(p_344859_)
            .addButton(CommonComponents.GUI_CONTINUE, p_345991_)
            .addButton(CommonComponents.GUI_CANCEL, PopupScreen::onClose)
            .build();
    }

    public static PopupScreen warningPopupScreen(Screen p_345623_, Component p_346338_, Consumer<PopupScreen> p_345246_) {
        return new PopupScreen.Builder(p_345623_, WARNING)
            .setMessage(p_346338_)
            .addButton(CommonComponents.GUI_CONTINUE, p_345246_)
            .addButton(CommonComponents.GUI_CANCEL, PopupScreen::onClose)
            .build();
    }

    public static PopupScreen warningAcknowledgePopupScreen(Screen p_345088_, Component p_344868_, Consumer<PopupScreen> p_344792_) {
        return new PopupScreen.Builder(p_345088_, WARNING).setMessage(p_344868_).addButton(CommonComponents.GUI_OK, p_344792_).build();
    }
}
