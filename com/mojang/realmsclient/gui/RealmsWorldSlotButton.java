package com.mojang.realmsclient.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.realmsclient.RealmsMainScreen;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.dto.RealmsWorldOptions;
import com.mojang.realmsclient.util.RealmsTextureManager;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RealmsWorldSlotButton extends Button {
    private static final ResourceLocation SLOT_FRAME_SPRITE = ResourceLocation.withDefaultNamespace("widget/slot_frame");
    private static final ResourceLocation CHECKMARK_SPRITE = ResourceLocation.withDefaultNamespace("icon/checkmark");
    public static final ResourceLocation EMPTY_SLOT_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/realms/empty_frame.png");
    public static final ResourceLocation DEFAULT_WORLD_SLOT_1 = ResourceLocation.withDefaultNamespace("textures/gui/title/background/panorama_0.png");
    public static final ResourceLocation DEFAULT_WORLD_SLOT_2 = ResourceLocation.withDefaultNamespace("textures/gui/title/background/panorama_2.png");
    public static final ResourceLocation DEFAULT_WORLD_SLOT_3 = ResourceLocation.withDefaultNamespace("textures/gui/title/background/panorama_3.png");
    private static final Component SLOT_ACTIVE_TOOLTIP = Component.translatable("mco.configure.world.slot.tooltip.active");
    private static final Component SWITCH_TO_MINIGAME_SLOT_TOOLTIP = Component.translatable("mco.configure.world.slot.tooltip.minigame");
    private static final Component SWITCH_TO_WORLD_SLOT_TOOLTIP = Component.translatable("mco.configure.world.slot.tooltip");
    static final Component MINIGAME = Component.translatable("mco.worldSlot.minigame");
    private final int slotIndex;
    @Nullable
    private RealmsWorldSlotButton.State state;

    public RealmsWorldSlotButton(int p_87929_, int p_87930_, int p_87931_, int p_87932_, int p_87935_, Button.OnPress p_87936_) {
        super(p_87929_, p_87930_, p_87931_, p_87932_, CommonComponents.EMPTY, p_87936_, DEFAULT_NARRATION);
        this.slotIndex = p_87935_;
    }

    @Nullable
    public RealmsWorldSlotButton.State getState() {
        return this.state;
    }

    public void setServerData(RealmsServer p_307266_) {
        this.state = new RealmsWorldSlotButton.State(p_307266_, this.slotIndex);
        this.setTooltipAndNarration(this.state, p_307266_.minigameName);
    }

    private void setTooltipAndNarration(RealmsWorldSlotButton.State p_307359_, @Nullable String p_307244_) {
        Component component = switch (p_307359_.action) {
            case SWITCH_SLOT -> p_307359_.minigame ? SWITCH_TO_MINIGAME_SLOT_TOOLTIP : SWITCH_TO_WORLD_SLOT_TOOLTIP;
            case JOIN -> SLOT_ACTIVE_TOOLTIP;
            default -> null;
        };
        if (component != null) {
            this.setTooltip(Tooltip.create(component));
        }

        MutableComponent mutablecomponent = Component.literal(p_307359_.slotName);
        if (p_307359_.minigame && p_307244_ != null) {
            mutablecomponent = mutablecomponent.append(CommonComponents.SPACE).append(p_307244_);
        }

        this.setMessage(mutablecomponent);
    }

    static RealmsWorldSlotButton.Action getAction(RealmsServer p_87960_, boolean p_87961_, boolean p_87962_) {
        if (p_87961_ && !p_87960_.expired && p_87960_.state != RealmsServer.State.UNINITIALIZED) {
            return RealmsWorldSlotButton.Action.JOIN;
        } else {
            return p_87961_ || p_87962_ && p_87960_.expired ? RealmsWorldSlotButton.Action.NOTHING : RealmsWorldSlotButton.Action.SWITCH_SLOT;
        }
    }

    @Override
    public void renderWidget(GuiGraphics p_282947_, int p_87965_, int p_87966_, float p_87967_) {
        if (this.state != null) {
            int i = this.getX();
            int j = this.getY();
            boolean flag = this.isHoveredOrFocused();
            ResourceLocation resourcelocation;
            if (this.state.minigame) {
                resourcelocation = RealmsTextureManager.worldTemplate(String.valueOf(this.state.imageId), this.state.image);
            } else if (this.state.empty) {
                resourcelocation = EMPTY_SLOT_LOCATION;
            } else if (this.state.image != null && this.state.imageId != -1L) {
                resourcelocation = RealmsTextureManager.worldTemplate(String.valueOf(this.state.imageId), this.state.image);
            } else if (this.slotIndex == 1) {
                resourcelocation = DEFAULT_WORLD_SLOT_1;
            } else if (this.slotIndex == 2) {
                resourcelocation = DEFAULT_WORLD_SLOT_2;
            } else if (this.slotIndex == 3) {
                resourcelocation = DEFAULT_WORLD_SLOT_3;
            } else {
                resourcelocation = EMPTY_SLOT_LOCATION;
            }

            if (this.state.isCurrentlyActiveSlot) {
                p_282947_.setColor(0.56F, 0.56F, 0.56F, 1.0F);
            }

            p_282947_.blit(resourcelocation, i + 3, j + 3, 0.0F, 0.0F, 74, 74, 74, 74);
            boolean flag1 = flag && this.state.action != RealmsWorldSlotButton.Action.NOTHING;
            if (flag1) {
                p_282947_.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            } else if (this.state.isCurrentlyActiveSlot) {
                p_282947_.setColor(0.8F, 0.8F, 0.8F, 1.0F);
            } else {
                p_282947_.setColor(0.56F, 0.56F, 0.56F, 1.0F);
            }

            p_282947_.blitSprite(SLOT_FRAME_SPRITE, i, j, 80, 80);
            p_282947_.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            if (this.state.isCurrentlyActiveSlot) {
                RenderSystem.enableBlend();
                p_282947_.blitSprite(CHECKMARK_SPRITE, i + 67, j + 4, 9, 8);
                RenderSystem.disableBlend();
            }

            Font font = Minecraft.getInstance().font;
            p_282947_.drawCenteredString(font, this.state.slotName, i + 40, j + 66, -1);
            p_282947_.drawCenteredString(
                font, RealmsMainScreen.getVersionComponent(this.state.slotVersion, this.state.compatibility.isCompatible()), i + 40, j + 80 + 2, -1
            );
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static enum Action {
        NOTHING,
        SWITCH_SLOT,
        JOIN;
    }

    @OnlyIn(Dist.CLIENT)
    public static class State {
        final boolean isCurrentlyActiveSlot;
        final String slotName;
        final String slotVersion;
        final RealmsServer.Compatibility compatibility;
        final long imageId;
        @Nullable
        final String image;
        public final boolean empty;
        public final boolean minigame;
        public final RealmsWorldSlotButton.Action action;

        public State(RealmsServer p_307209_, int p_307377_) {
            this.minigame = p_307377_ == 4;
            if (this.minigame) {
                this.isCurrentlyActiveSlot = p_307209_.isMinigameActive();
                this.slotName = RealmsWorldSlotButton.MINIGAME.getString();
                this.imageId = (long)p_307209_.minigameId;
                this.image = p_307209_.minigameImage;
                this.empty = p_307209_.minigameId == -1;
                this.slotVersion = "";
                this.compatibility = RealmsServer.Compatibility.UNVERIFIABLE;
            } else {
                RealmsWorldOptions realmsworldoptions = p_307209_.slots.get(p_307377_);
                this.isCurrentlyActiveSlot = p_307209_.activeSlot == p_307377_ && !p_307209_.isMinigameActive();
                this.slotName = realmsworldoptions.getSlotName(p_307377_);
                this.imageId = realmsworldoptions.templateId;
                this.image = realmsworldoptions.templateImage;
                this.empty = realmsworldoptions.empty;
                this.slotVersion = realmsworldoptions.version;
                this.compatibility = realmsworldoptions.compatibility;
            }

            this.action = RealmsWorldSlotButton.getAction(p_307209_, this.isCurrentlyActiveSlot, this.minigame);
        }
    }
}
