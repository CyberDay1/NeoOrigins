package net.minecraft.client.gui.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ErrorScreen extends Screen {
    private final Component message;

    public ErrorScreen(Component p_96049_, Component p_96050_) {
        super(p_96049_);
        this.message = p_96050_;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_CANCEL, p_280801_ -> this.minecraft.setScreen(null)).bounds(this.width / 2 - 100, 140, 200, 20).build()
        );
    }

    @Override
    public void render(GuiGraphics p_281469_, int p_96053_, int p_96054_, float p_96055_) {
        super.render(p_281469_, p_96053_, p_96054_, p_96055_);
        p_281469_.drawCenteredString(this.font, this.title, this.width / 2, 90, 16777215);
        p_281469_.drawCenteredString(this.font, this.message, this.width / 2, 110, 16777215);
    }

    @Override
    public void renderBackground(GuiGraphics p_294505_, int p_295605_, int p_295169_, float p_296050_) {
        p_294505_.fillGradient(0, 0, this.width, this.height, -12574688, -11530224);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
