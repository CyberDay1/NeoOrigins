package com.mojang.realmsclient.gui.screens;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.RealmsMainScreen;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.PendingInvite;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.gui.RealmsDataFetcher;
import com.mojang.realmsclient.gui.RowButton;
import com.mojang.realmsclient.util.RealmsUtil;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.realms.RealmsObjectSelectionList;
import net.minecraft.realms.RealmsScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class RealmsPendingInvitesScreen extends RealmsScreen {
    static final ResourceLocation ACCEPT_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("pending_invite/accept_highlighted");
    static final ResourceLocation ACCEPT_SPRITE = ResourceLocation.withDefaultNamespace("pending_invite/accept");
    static final ResourceLocation REJECT_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("pending_invite/reject_highlighted");
    static final ResourceLocation REJECT_SPRITE = ResourceLocation.withDefaultNamespace("pending_invite/reject");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component NO_PENDING_INVITES_TEXT = Component.translatable("mco.invites.nopending");
    static final Component ACCEPT_INVITE = Component.translatable("mco.invites.button.accept");
    static final Component REJECT_INVITE = Component.translatable("mco.invites.button.reject");
    private final Screen lastScreen;
    private final CompletableFuture<List<PendingInvite>> pendingInvites = CompletableFuture.supplyAsync(() -> {
        try {
            return RealmsClient.create().pendingInvites().pendingInvites;
        } catch (RealmsServiceException realmsserviceexception) {
            LOGGER.error("Couldn't list invites", (Throwable)realmsserviceexception);
            return List.of();
        }
    }, Util.ioPool());
    @Nullable
    Component toolTip;
    RealmsPendingInvitesScreen.PendingInvitationSelectionList pendingInvitationSelectionList;
    int selectedInvite = -1;
    private Button acceptButton;
    private Button rejectButton;

    public RealmsPendingInvitesScreen(Screen p_279260_, Component p_279122_) {
        super(p_279122_);
        this.lastScreen = p_279260_;
    }

    @Override
    public void init() {
        RealmsMainScreen.refreshPendingInvites();
        this.pendingInvitationSelectionList = new RealmsPendingInvitesScreen.PendingInvitationSelectionList();
        this.pendingInvites.thenAcceptAsync(p_308444_ -> {
            List<RealmsPendingInvitesScreen.Entry> list = p_308444_.stream().map(p_293579_ -> new RealmsPendingInvitesScreen.Entry(p_293579_)).toList();
            this.pendingInvitationSelectionList.replaceEntries(list);
            if (list.isEmpty()) {
                this.minecraft.getNarrator().say(NO_PENDING_INVITES_TEXT);
            }
        }, this.screenExecutor);
        this.addRenderableWidget(this.pendingInvitationSelectionList);
        this.acceptButton = this.addRenderableWidget(Button.builder(ACCEPT_INVITE, p_293576_ -> {
            this.handleInvitation(this.selectedInvite, true);
            this.selectedInvite = -1;
            this.updateButtonStates();
        }).bounds(this.width / 2 - 174, this.height - 32, 100, 20).build());
        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_DONE, p_293580_ -> this.onClose()).bounds(this.width / 2 - 50, this.height - 32, 100, 20).build()
        );
        this.rejectButton = this.addRenderableWidget(Button.builder(REJECT_INVITE, p_293581_ -> {
            this.handleInvitation(this.selectedInvite, false);
            this.selectedInvite = -1;
            this.updateButtonStates();
        }).bounds(this.width / 2 + 74, this.height - 32, 100, 20).build());
        this.updateButtonStates();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    void handleInvitation(int p_294942_, boolean p_294885_) {
        if (p_294942_ < this.pendingInvitationSelectionList.getItemCount()) {
            String s = this.pendingInvitationSelectionList.children().get(p_294942_).pendingInvite.invitationId;
            CompletableFuture.<Boolean>supplyAsync(() -> {
                try {
                    RealmsClient realmsclient = RealmsClient.create();
                    if (p_294885_) {
                        realmsclient.acceptInvitation(s);
                    } else {
                        realmsclient.rejectInvitation(s);
                    }

                    return true;
                } catch (RealmsServiceException realmsserviceexception) {
                    LOGGER.error("Couldn't handle invite", (Throwable)realmsserviceexception);
                    return false;
                }
            }, Util.ioPool()).thenAcceptAsync(p_293575_ -> {
                if (p_293575_) {
                    this.pendingInvitationSelectionList.removeAtIndex(p_294942_);
                    RealmsDataFetcher realmsdatafetcher = this.minecraft.realmsDataFetcher();
                    if (p_294885_) {
                        realmsdatafetcher.serverListUpdateTask.reset();
                    }

                    realmsdatafetcher.pendingInvitesTask.reset();
                }
            }, this.screenExecutor);
        }
    }

    @Override
    public void render(GuiGraphics p_282787_, int p_88900_, int p_88901_, float p_88902_) {
        super.render(p_282787_, p_88900_, p_88901_, p_88902_);
        this.toolTip = null;
        p_282787_.drawCenteredString(this.font, this.title, this.width / 2, 12, -1);
        if (this.toolTip != null) {
            p_282787_.renderTooltip(this.font, this.toolTip, p_88900_, p_88901_);
        }

        if (this.pendingInvites.isDone() && this.pendingInvitationSelectionList.getItemCount() == 0) {
            p_282787_.drawCenteredString(this.font, NO_PENDING_INVITES_TEXT, this.width / 2, this.height / 2 - 20, -1);
        }
    }

    void updateButtonStates() {
        this.acceptButton.visible = this.shouldAcceptAndRejectButtonBeVisible(this.selectedInvite);
        this.rejectButton.visible = this.shouldAcceptAndRejectButtonBeVisible(this.selectedInvite);
    }

    private boolean shouldAcceptAndRejectButtonBeVisible(int p_88963_) {
        return p_88963_ != -1;
    }

    @OnlyIn(Dist.CLIENT)
    class Entry extends ObjectSelectionList.Entry<RealmsPendingInvitesScreen.Entry> {
        private static final int TEXT_LEFT = 38;
        final PendingInvite pendingInvite;
        private final List<RowButton> rowButtons;

        Entry(PendingInvite p_88996_) {
            this.pendingInvite = p_88996_;
            this.rowButtons = Arrays.asList(new RealmsPendingInvitesScreen.Entry.AcceptRowButton(), new RealmsPendingInvitesScreen.Entry.RejectRowButton());
        }

        @Override
        public void render(
            GuiGraphics p_281445_,
            int p_281806_,
            int p_283610_,
            int p_282909_,
            int p_281705_,
            int p_281977_,
            int p_282983_,
            int p_281655_,
            boolean p_282274_,
            float p_282862_
        ) {
            this.renderPendingInvitationItem(p_281445_, this.pendingInvite, p_282909_, p_283610_, p_282983_, p_281655_);
        }

        @Override
        public boolean mouseClicked(double p_88998_, double p_88999_, int p_89000_) {
            RowButton.rowButtonMouseClicked(RealmsPendingInvitesScreen.this.pendingInvitationSelectionList, this, this.rowButtons, p_89000_, p_88998_, p_88999_);
            return super.mouseClicked(p_88998_, p_88999_, p_89000_);
        }

        private void renderPendingInvitationItem(GuiGraphics p_281764_, PendingInvite p_282748_, int p_282810_, int p_282994_, int p_283639_, int p_283659_) {
            p_281764_.drawString(RealmsPendingInvitesScreen.this.font, p_282748_.realmName, p_282810_ + 38, p_282994_ + 1, -1, false);
            p_281764_.drawString(RealmsPendingInvitesScreen.this.font, p_282748_.realmOwnerName, p_282810_ + 38, p_282994_ + 12, 7105644, false);
            p_281764_.drawString(
                RealmsPendingInvitesScreen.this.font,
                RealmsUtil.convertToAgePresentationFromInstant(p_282748_.date),
                p_282810_ + 38,
                p_282994_ + 24,
                7105644,
                false
            );
            RowButton.drawButtonsInRow(
                p_281764_, this.rowButtons, RealmsPendingInvitesScreen.this.pendingInvitationSelectionList, p_282810_, p_282994_, p_283639_, p_283659_
            );
            RealmsUtil.renderPlayerFace(p_281764_, p_282810_, p_282994_, 32, p_282748_.realmOwnerUuid);
        }

        @Override
        public Component getNarration() {
            Component component = CommonComponents.joinLines(
                Component.literal(this.pendingInvite.realmName),
                Component.literal(this.pendingInvite.realmOwnerName),
                RealmsUtil.convertToAgePresentationFromInstant(this.pendingInvite.date)
            );
            return Component.translatable("narrator.select", component);
        }

        @OnlyIn(Dist.CLIENT)
        class AcceptRowButton extends RowButton {
            AcceptRowButton() {
                super(15, 15, 215, 5);
            }

            @Override
            protected void draw(GuiGraphics p_282151_, int p_283695_, int p_282436_, boolean p_282168_) {
                p_282151_.blitSprite(
                    p_282168_ ? RealmsPendingInvitesScreen.ACCEPT_HIGHLIGHTED_SPRITE : RealmsPendingInvitesScreen.ACCEPT_SPRITE, p_283695_, p_282436_, 18, 18
                );
                if (p_282168_) {
                    RealmsPendingInvitesScreen.this.toolTip = RealmsPendingInvitesScreen.ACCEPT_INVITE;
                }
            }

            @Override
            public void onClick(int p_89029_) {
                RealmsPendingInvitesScreen.this.handleInvitation(p_89029_, true);
            }
        }

        @OnlyIn(Dist.CLIENT)
        class RejectRowButton extends RowButton {
            RejectRowButton() {
                super(15, 15, 235, 5);
            }

            @Override
            protected void draw(GuiGraphics p_282457_, int p_281421_, int p_281260_, boolean p_281476_) {
                p_282457_.blitSprite(
                    p_281476_ ? RealmsPendingInvitesScreen.REJECT_HIGHLIGHTED_SPRITE : RealmsPendingInvitesScreen.REJECT_SPRITE, p_281421_, p_281260_, 18, 18
                );
                if (p_281476_) {
                    RealmsPendingInvitesScreen.this.toolTip = RealmsPendingInvitesScreen.REJECT_INVITE;
                }
            }

            @Override
            public void onClick(int p_89039_) {
                RealmsPendingInvitesScreen.this.handleInvitation(p_89039_, false);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    class PendingInvitationSelectionList extends RealmsObjectSelectionList<RealmsPendingInvitesScreen.Entry> {
        public PendingInvitationSelectionList() {
            super(RealmsPendingInvitesScreen.this.width, RealmsPendingInvitesScreen.this.height - 72, 32, 36);
        }

        public void removeAtIndex(int p_89058_) {
            this.remove(p_89058_);
        }

        @Override
        public int getMaxPosition() {
            return this.getItemCount() * 36;
        }

        @Override
        public int getRowWidth() {
            return 260;
        }

        @Override
        public void selectItem(int p_89049_) {
            super.selectItem(p_89049_);
            this.selectInviteListItem(p_89049_);
        }

        public void selectInviteListItem(int p_89061_) {
            RealmsPendingInvitesScreen.this.selectedInvite = p_89061_;
            RealmsPendingInvitesScreen.this.updateButtonStates();
        }

        public void setSelected(@Nullable RealmsPendingInvitesScreen.Entry p_89053_) {
            super.setSelected(p_89053_);
            RealmsPendingInvitesScreen.this.selectedInvite = this.children().indexOf(p_89053_);
            RealmsPendingInvitesScreen.this.updateButtonStates();
        }
    }
}
