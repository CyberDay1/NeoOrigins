package com.mojang.realmsclient.gui.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.FittingMultiLineTextWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.realms.RealmsScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.CommonLinks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AddRealmPopupScreen extends RealmsScreen {
    private static final Component POPUP_TEXT = Component.translatable("mco.selectServer.popup");
    private static final Component CLOSE_TEXT = Component.translatable("mco.selectServer.close");
    private static final ResourceLocation BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("popup/background");
    private static final ResourceLocation TRIAL_AVAILABLE_SPRITE = ResourceLocation.withDefaultNamespace("icon/trial_available");
    private static final WidgetSprites CROSS_BUTTON_SPRITES = new WidgetSprites(
        ResourceLocation.withDefaultNamespace("widget/cross_button"), ResourceLocation.withDefaultNamespace("widget/cross_button_highlighted")
    );
    private static final int BG_TEXTURE_WIDTH = 236;
    private static final int BG_TEXTURE_HEIGHT = 34;
    private static final int BG_BORDER_SIZE = 6;
    private static final int IMAGE_WIDTH = 195;
    private static final int IMAGE_HEIGHT = 152;
    private static final int BUTTON_SPACING = 4;
    private static final int PADDING = 10;
    private static final int WIDTH = 320;
    private static final int HEIGHT = 172;
    private static final int TEXT_WIDTH = 100;
    private static final int BUTTON_WIDTH = 99;
    private static final int CAROUSEL_SWITCH_INTERVAL = 100;
    private static List<ResourceLocation> carouselImages = List.of();
    private final Screen backgroundScreen;
    private final boolean trialAvailable;
    @Nullable
    private Button createTrialButton;
    private int carouselIndex;
    private int carouselTick;

    public AddRealmPopupScreen(Screen p_344774_, boolean p_345980_) {
        super(POPUP_TEXT);
        this.backgroundScreen = p_344774_;
        this.trialAvailable = p_345980_;
    }

    public static void updateCarouselImages(ResourceManager p_346428_) {
        Collection<ResourceLocation> collection = p_346428_.listResources("textures/gui/images", p_345740_ -> p_345740_.getPath().endsWith(".png")).keySet();
        carouselImages = collection.stream().filter(p_345782_ -> p_345782_.getNamespace().equals("realms")).toList();
    }

    @Override
    protected void init() {
        this.backgroundScreen.resize(this.minecraft, this.width, this.height);
        if (this.trialAvailable) {
            this.createTrialButton = this.addRenderableWidget(
                Button.builder(Component.translatable("mco.selectServer.trial"), ConfirmLinkScreen.confirmLink(this, CommonLinks.START_REALMS_TRIAL))
                    .bounds(this.right() - 10 - 99, this.bottom() - 10 - 4 - 40, 99, 20)
                    .build()
            );
        }

        this.addRenderableWidget(
            Button.builder(Component.translatable("mco.selectServer.buy"), ConfirmLinkScreen.confirmLink(this, CommonLinks.BUY_REALMS))
                .bounds(this.right() - 10 - 99, this.bottom() - 10 - 20, 99, 20)
                .build()
        );
        ImageButton imagebutton = this.addRenderableWidget(
            new ImageButton(this.left() + 4, this.top() + 4, 14, 14, CROSS_BUTTON_SPRITES, p_345293_ -> this.onClose(), CLOSE_TEXT)
        );
        imagebutton.setTooltip(Tooltip.create(CLOSE_TEXT));
        int i = 142 - (this.trialAvailable ? 40 : 20);
        FittingMultiLineTextWidget fittingmultilinetextwidget = new FittingMultiLineTextWidget(
            this.right() - 10 - 100, this.top() + 10, 100, i, POPUP_TEXT, this.font
        );
        if (fittingmultilinetextwidget.showingScrollBar()) {
            fittingmultilinetextwidget.setWidth(100 - fittingmultilinetextwidget.scrollbarWidth());
        }

        this.addRenderableWidget(fittingmultilinetextwidget);
    }

    @Override
    public void tick() {
        super.tick();
        if (++this.carouselTick > 100) {
            this.carouselTick = 0;
            this.carouselIndex = (this.carouselIndex + 1) % carouselImages.size();
        }
    }

    @Override
    public void render(GuiGraphics p_345725_, int p_345936_, int p_345896_, float p_345975_) {
        super.render(p_345725_, p_345936_, p_345896_, p_345975_);
        if (this.createTrialButton != null) {
            renderDiamond(p_345725_, this.createTrialButton);
        }
    }

    public static void renderDiamond(GuiGraphics p_344936_, Button p_345090_) {
        int i = 8;
        p_344936_.pose().pushPose();
        p_344936_.pose().translate(0.0F, 0.0F, 110.0F);
        p_344936_.blitSprite(TRIAL_AVAILABLE_SPRITE, p_345090_.getX() + p_345090_.getWidth() - 8 - 4, p_345090_.getY() + p_345090_.getHeight() / 2 - 4, 8, 8);
        p_344936_.pose().popPose();
    }

    @Override
    public void renderBackground(GuiGraphics p_346027_, int p_345796_, int p_345649_, float p_345603_) {
        this.backgroundScreen.render(p_346027_, -1, -1, p_345603_);
        p_346027_.flush();
        RenderSystem.clear(256, Minecraft.ON_OSX);
        this.clearTooltipForNextRenderPass();
        this.renderTransparentBackground(p_346027_);
        p_346027_.blitSprite(BACKGROUND_SPRITE, this.left(), this.top(), 320, 172);
        if (!carouselImages.isEmpty()) {
            p_346027_.blit(carouselImages.get(this.carouselIndex), this.left() + 10, this.top() + 10, 0, 0.0F, 0.0F, 195, 152, 195, 152);
        }
    }

    private int left() {
        return (this.width - 320) / 2;
    }

    private int top() {
        return (this.height - 172) / 2;
    }

    private int right() {
        return this.left() + 320;
    }

    private int bottom() {
        return this.top() + 172;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.backgroundScreen);
    }
}
