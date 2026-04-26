package net.minecraft.client.gui.screens.options.controls;

import com.mojang.blaze3d.platform.InputConstants;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class KeyBindsScreen extends OptionsSubScreen {
    private static final Component TITLE = Component.translatable("controls.keybinds.title");
    @Nullable
    public KeyMapping selectedKey;
    public long lastKeySelection;
    private KeyBindsList keyBindsList;
    private Button resetButton;
    // Neo: These are to hold the last key and modifier pressed so they can be checked in keyReleased
    private InputConstants.Key lastPressedKey = InputConstants.UNKNOWN;
    private InputConstants.Key lastPressedModifier = InputConstants.UNKNOWN;
    private boolean isLastKeyHeldDown = false;
    private boolean isLastModifierHeldDown = false;

    public KeyBindsScreen(Screen p_344996_, Options p_344771_) {
        super(p_344996_, p_344771_, TITLE);
    }

    @Override
    protected void addContents() {
        this.keyBindsList = this.layout.addToContents(new KeyBindsList(this, this.minecraft));
    }

    @Override
    protected void addOptions() {
    }

    @Override
    protected void addFooter() {
        this.resetButton = Button.builder(Component.translatable("controls.resetAll"), p_346345_ -> {
            for (KeyMapping keymapping : this.options.keyMappings) {
                keymapping.setToDefault();
            }

            this.keyBindsList.resetMappingAndUpdateButtons();
        }).build();
        LinearLayout linearlayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        linearlayout.addChild(this.resetButton);
        linearlayout.addChild(Button.builder(CommonComponents.GUI_DONE, p_345169_ -> this.onClose()).build());
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        this.keyBindsList.updateSize(this.width, this.layout);
    }

    @Override
    public boolean mouseClicked(double p_346251_, double p_345041_, int p_345147_) {
        if (this.selectedKey != null) {
            this.options.setKey(this.selectedKey, InputConstants.Type.MOUSE.getOrCreate(p_345147_));
            this.selectedKey = null;
            this.keyBindsList.resetMappingAndUpdateButtons();
            return true;
        } else {
            return super.mouseClicked(p_346251_, p_345041_, p_345147_);
        }
    }

    @Override
    public boolean keyPressed(int p_345810_, int p_345447_, int p_344981_) {
        if (this.selectedKey != null) {
            var key = InputConstants.getKey(p_345810_, p_345447_);
            if (lastPressedModifier == InputConstants.UNKNOWN && net.neoforged.neoforge.client.settings.KeyModifier.isKeyCodeModifier(key)) {
                lastPressedModifier = key;
                isLastModifierHeldDown = true;
            } else {
                lastPressedKey = key;
                isLastKeyHeldDown = true;
            }
            return true;
        } else {
            return super.keyPressed(p_345810_, p_345447_, p_344981_);
        }
    }

    // Neo: This method is overridden to more easily handle modifier keys
    @Override
    public boolean keyReleased(int p_94715_, int p_94716_, int p_94717_) {
        // We ignore events from keys with the scan code 63 as they're emitted
        // (only as RELEASE, not PRESS) by Mac systems to indicate that "Fn" is being pressed
        // See https://github.com/neoforged/NeoForge/issues/1683
        if (this.selectedKey != null && (!net.minecraft.client.Minecraft.ON_OSX || p_94716_ != 63)) {
            if (p_94715_ == 256) {
                this.selectedKey.setKeyModifierAndCode(net.neoforged.neoforge.client.settings.KeyModifier.NONE, InputConstants.UNKNOWN);
                this.options.setKey(this.selectedKey, InputConstants.UNKNOWN);
                lastPressedKey = InputConstants.UNKNOWN;
                lastPressedModifier = InputConstants.UNKNOWN;
                isLastKeyHeldDown = false;
                isLastModifierHeldDown = false;
            } else {
                var key = InputConstants.getKey(p_94715_, p_94716_);
                if (lastPressedKey.equals(key)) {
                    isLastKeyHeldDown = false;
                } else if (lastPressedModifier.equals(key)) {
                    isLastModifierHeldDown = false;
                }

                if (!isLastKeyHeldDown && !isLastModifierHeldDown) {
                    if (!lastPressedKey.equals(InputConstants.UNKNOWN)) {
                        this.selectedKey.setKeyModifierAndCode(
                                net.neoforged.neoforge.client.settings.KeyModifier.getKeyModifier(lastPressedModifier),
                                lastPressedKey
                        );
                        this.options.setKey(this.selectedKey, lastPressedKey);
                    } else {
                        this.selectedKey.setKeyModifierAndCode(
                                net.neoforged.neoforge.client.settings.KeyModifier.NONE,
                                lastPressedModifier
                        );
                        this.options.setKey(this.selectedKey, lastPressedModifier);
                    }
                    lastPressedKey = InputConstants.UNKNOWN;
                    lastPressedModifier = InputConstants.UNKNOWN;
                } else {
                    return true;
                }
            }
            this.selectedKey = null;
            this.lastKeySelection = Util.getMillis();
            this.keyBindsList.resetMappingAndUpdateButtons();
            return true;
        } else {
            return super.keyReleased(p_94715_, p_94716_, p_94717_);
        }
    }

    @Override
    public void render(GuiGraphics p_346209_, int p_344846_, int p_346350_, float p_345601_) {
        super.render(p_346209_, p_344846_, p_346350_, p_345601_);
        boolean flag = false;

        for (KeyMapping keymapping : this.options.keyMappings) {
            if (!keymapping.isDefault()) {
                flag = true;
                break;
            }
        }

        this.resetButton.active = flag;
    }
}
