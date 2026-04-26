package com.mojang.realmsclient.gui.screens;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.dto.WorldTemplate;
import com.mojang.realmsclient.dto.WorldTemplatePaginatedList;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.util.WorldGenerationInfo;
import com.mojang.realmsclient.util.task.LongRunningTask;
import com.mojang.realmsclient.util.task.RealmCreationTask;
import com.mojang.realmsclient.util.task.ResettingGeneratedWorldTask;
import com.mojang.realmsclient.util.task.ResettingTemplateWorldTask;
import com.mojang.realmsclient.util.task.SwitchSlotTask;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.realms.RealmsScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class RealmsResetWorldScreen extends RealmsScreen {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final Component CREATE_REALM_TITLE = Component.translatable("mco.selectServer.create");
    private static final Component CREATE_REALM_SUBTITLE = Component.translatable("mco.selectServer.create.subtitle");
    private static final Component CREATE_WORLD_TITLE = Component.translatable("mco.configure.world.switch.slot");
    private static final Component CREATE_WORLD_SUBTITLE = Component.translatable("mco.configure.world.switch.slot.subtitle");
    private static final Component RESET_WORLD_TITLE = Component.translatable("mco.reset.world.title");
    private static final Component RESET_WORLD_SUBTITLE = Component.translatable("mco.reset.world.warning");
    public static final Component CREATE_WORLD_RESET_TASK_TITLE = Component.translatable("mco.create.world.reset.title");
    private static final Component RESET_WORLD_RESET_TASK_TITLE = Component.translatable("mco.reset.world.resetting.screen.title");
    private static final Component WORLD_TEMPLATES_TITLE = Component.translatable("mco.reset.world.template");
    private static final Component ADVENTURES_TITLE = Component.translatable("mco.reset.world.adventure");
    private static final Component EXPERIENCES_TITLE = Component.translatable("mco.reset.world.experience");
    private static final Component INSPIRATION_TITLE = Component.translatable("mco.reset.world.inspiration");
    private final Screen lastScreen;
    private final RealmsServer serverData;
    private final Component subtitle;
    private final int subtitleColor;
    private final Component resetTaskTitle;
    private static final ResourceLocation UPLOAD_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/realms/upload.png");
    private static final ResourceLocation ADVENTURE_MAP_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/realms/adventure.png");
    private static final ResourceLocation SURVIVAL_SPAWN_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/realms/survival_spawn.png");
    private static final ResourceLocation NEW_WORLD_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/realms/new_world.png");
    private static final ResourceLocation EXPERIENCE_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/realms/experience.png");
    private static final ResourceLocation INSPIRATION_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/realms/inspiration.png");
    WorldTemplatePaginatedList templates;
    WorldTemplatePaginatedList adventuremaps;
    WorldTemplatePaginatedList experiences;
    WorldTemplatePaginatedList inspirations;
    public final int slot;
    @Nullable
    private final RealmCreationTask realmCreationTask;
    private final Runnable resetWorldRunnable;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    private RealmsResetWorldScreen(
        Screen p_89329_, RealmsServer p_89330_, int p_300857_, Component p_301121_, Component p_300859_, int p_300978_, Component p_300942_, Runnable p_89331_
    ) {
        this(p_89329_, p_89330_, p_300857_, p_301121_, p_300859_, p_300978_, p_300942_, null, p_89331_);
    }

    public RealmsResetWorldScreen(
        Screen p_306282_,
        RealmsServer p_306292_,
        int p_305906_,
        Component p_306014_,
        Component p_306028_,
        int p_306044_,
        Component p_306316_,
        @Nullable RealmCreationTask p_320691_,
        Runnable p_306029_
    ) {
        super(p_306014_);
        this.lastScreen = p_306282_;
        this.serverData = p_306292_;
        this.slot = p_305906_;
        this.subtitle = p_306028_;
        this.subtitleColor = p_306044_;
        this.resetTaskTitle = p_306316_;
        this.realmCreationTask = p_320691_;
        this.resetWorldRunnable = p_306029_;
    }

    public static RealmsResetWorldScreen forNewRealm(Screen p_300881_, RealmsServer p_301228_, RealmCreationTask p_320942_, Runnable p_300926_) {
        return new RealmsResetWorldScreen(
            p_300881_,
            p_301228_,
            p_301228_.activeSlot,
            CREATE_REALM_TITLE,
            CREATE_REALM_SUBTITLE,
            -6250336,
            CREATE_WORLD_RESET_TASK_TITLE,
            p_320942_,
            p_300926_
        );
    }

    public static RealmsResetWorldScreen forEmptySlot(Screen p_300943_, int p_301275_, RealmsServer p_301312_, Runnable p_301297_) {
        return new RealmsResetWorldScreen(
            p_300943_, p_301312_, p_301275_, CREATE_WORLD_TITLE, CREATE_WORLD_SUBTITLE, -6250336, CREATE_WORLD_RESET_TASK_TITLE, p_301297_
        );
    }

    public static RealmsResetWorldScreen forResetSlot(Screen p_301039_, RealmsServer p_300914_, Runnable p_301017_) {
        return new RealmsResetWorldScreen(
            p_301039_, p_300914_, p_300914_.activeSlot, RESET_WORLD_TITLE, RESET_WORLD_SUBTITLE, -65536, RESET_WORLD_RESET_TASK_TITLE, p_301017_
        );
    }

    @Override
    public void init() {
        LinearLayout linearlayout = this.layout.addToHeader(LinearLayout.vertical());
        linearlayout.defaultCellSetting().padding(9 / 3);
        linearlayout.addChild(new StringWidget(this.title, this.font), LayoutSettings::alignHorizontallyCenter);
        linearlayout.addChild(new StringWidget(this.subtitle, this.font).setColor(this.subtitleColor), LayoutSettings::alignHorizontallyCenter);
        (new Thread("Realms-reset-world-fetcher") {
            @Override
            public void run() {
                RealmsClient realmsclient = RealmsClient.create();

                try {
                    WorldTemplatePaginatedList worldtemplatepaginatedlist = realmsclient.fetchWorldTemplates(1, 10, RealmsServer.WorldType.NORMAL);
                    WorldTemplatePaginatedList worldtemplatepaginatedlist1 = realmsclient.fetchWorldTemplates(1, 10, RealmsServer.WorldType.ADVENTUREMAP);
                    WorldTemplatePaginatedList worldtemplatepaginatedlist2 = realmsclient.fetchWorldTemplates(1, 10, RealmsServer.WorldType.EXPERIENCE);
                    WorldTemplatePaginatedList worldtemplatepaginatedlist3 = realmsclient.fetchWorldTemplates(1, 10, RealmsServer.WorldType.INSPIRATION);
                    RealmsResetWorldScreen.this.minecraft.execute(() -> {
                        RealmsResetWorldScreen.this.templates = worldtemplatepaginatedlist;
                        RealmsResetWorldScreen.this.adventuremaps = worldtemplatepaginatedlist1;
                        RealmsResetWorldScreen.this.experiences = worldtemplatepaginatedlist2;
                        RealmsResetWorldScreen.this.inspirations = worldtemplatepaginatedlist3;
                    });
                } catch (RealmsServiceException realmsserviceexception) {
                    RealmsResetWorldScreen.LOGGER.error("Couldn't fetch templates in reset world", (Throwable)realmsserviceexception);
                }
            }
        }).start();
        GridLayout gridlayout = this.layout.addToContents(new GridLayout());
        GridLayout.RowHelper gridlayout$rowhelper = gridlayout.createRowHelper(3);
        gridlayout$rowhelper.defaultCellSetting().paddingHorizontal(16);
        gridlayout$rowhelper.addChild(
            new RealmsResetWorldScreen.FrameButton(
                this.minecraft.font,
                RealmsResetNormalWorldScreen.TITLE,
                NEW_WORLD_LOCATION,
                p_280746_ -> this.minecraft.setScreen(new RealmsResetNormalWorldScreen(this::generationSelectionCallback, this.title))
            )
        );
        gridlayout$rowhelper.addChild(
            new RealmsResetWorldScreen.FrameButton(
                this.minecraft.font,
                RealmsSelectFileToUploadScreen.TITLE,
                UPLOAD_LOCATION,
                p_319367_ -> this.minecraft.setScreen(new RealmsSelectFileToUploadScreen(this.realmCreationTask, this.serverData.id, this.slot, this))
            )
        );
        gridlayout$rowhelper.addChild(
            new RealmsResetWorldScreen.FrameButton(
                this.minecraft.font,
                WORLD_TEMPLATES_TITLE,
                SURVIVAL_SPAWN_LOCATION,
                p_300639_ -> this.minecraft
                        .setScreen(
                            new RealmsSelectWorldTemplateScreen(
                                WORLD_TEMPLATES_TITLE, this::templateSelectionCallback, RealmsServer.WorldType.NORMAL, this.templates
                            )
                        )
            )
        );
        gridlayout$rowhelper.addChild(SpacerElement.height(16), 3);
        gridlayout$rowhelper.addChild(
            new RealmsResetWorldScreen.FrameButton(
                this.minecraft.font,
                ADVENTURES_TITLE,
                ADVENTURE_MAP_LOCATION,
                p_300637_ -> this.minecraft
                        .setScreen(
                            new RealmsSelectWorldTemplateScreen(
                                ADVENTURES_TITLE, this::templateSelectionCallback, RealmsServer.WorldType.ADVENTUREMAP, this.adventuremaps
                            )
                        )
            )
        );
        gridlayout$rowhelper.addChild(
            new RealmsResetWorldScreen.FrameButton(
                this.minecraft.font,
                EXPERIENCES_TITLE,
                EXPERIENCE_LOCATION,
                p_300638_ -> this.minecraft
                        .setScreen(
                            new RealmsSelectWorldTemplateScreen(
                                EXPERIENCES_TITLE, this::templateSelectionCallback, RealmsServer.WorldType.EXPERIENCE, this.experiences
                            )
                        )
            )
        );
        gridlayout$rowhelper.addChild(
            new RealmsResetWorldScreen.FrameButton(
                this.minecraft.font,
                INSPIRATION_TITLE,
                INSPIRATION_LOCATION,
                p_300640_ -> this.minecraft
                        .setScreen(
                            new RealmsSelectWorldTemplateScreen(
                                INSPIRATION_TITLE, this::templateSelectionCallback, RealmsServer.WorldType.INSPIRATION, this.inspirations
                            )
                        )
            )
        );
        this.layout.addToFooter(Button.builder(CommonComponents.GUI_BACK, p_300644_ -> this.onClose()).build());
        this.layout.visitWidgets(p_321346_ -> {
            AbstractWidget abstractwidget = this.addRenderableWidget(p_321346_);
        });
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }

    @Override
    public Component getNarrationMessage() {
        return CommonComponents.joinForNarration(this.getTitle(), this.subtitle);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    private void templateSelectionCallback(@Nullable WorldTemplate p_167454_) {
        this.minecraft.setScreen(this);
        if (p_167454_ != null) {
            this.runResetTasks(new ResettingTemplateWorldTask(p_167454_, this.serverData.id, this.resetTaskTitle, this.resetWorldRunnable));
        }
    }

    private void generationSelectionCallback(@Nullable WorldGenerationInfo p_167456_) {
        this.minecraft.setScreen(this);
        if (p_167456_ != null) {
            this.runResetTasks(new ResettingGeneratedWorldTask(p_167456_, this.serverData.id, this.resetTaskTitle, this.resetWorldRunnable));
        }
    }

    private void runResetTasks(LongRunningTask p_305864_) {
        List<LongRunningTask> list = new ArrayList<>();
        if (this.realmCreationTask != null) {
            list.add(this.realmCreationTask);
        }

        if (this.slot != this.serverData.activeSlot) {
            list.add(new SwitchSlotTask(this.serverData.id, this.slot, () -> {
            }));
        }

        list.add(p_305864_);
        this.minecraft.setScreen(new RealmsLongRunningMcoTaskScreen(this.lastScreen, list.toArray(new LongRunningTask[0])));
    }

    @OnlyIn(Dist.CLIENT)
    class FrameButton extends Button {
        private static final ResourceLocation SLOT_FRAME_SPRITE = ResourceLocation.withDefaultNamespace("widget/slot_frame");
        private static final int FRAME_SIZE = 60;
        private static final int FRAME_WIDTH = 2;
        private static final int IMAGE_SIZE = 56;
        private final ResourceLocation image;

        FrameButton(Font p_335556_, Component p_89441_, ResourceLocation p_89442_, Button.OnPress p_89443_) {
            super(0, 0, 60, 60 + 9, p_89441_, p_89443_, DEFAULT_NARRATION);
            this.image = p_89442_;
        }

        @Override
        public void renderWidget(GuiGraphics p_282595_, int p_282741_, int p_283560_, float p_281923_) {
            boolean flag = this.isHoveredOrFocused();
            if (flag) {
                p_282595_.setColor(0.56F, 0.56F, 0.56F, 1.0F);
            }

            int i = this.getX();
            int j = this.getY();
            p_282595_.blit(this.image, i + 2, j + 2, 0.0F, 0.0F, 56, 56, 56, 56);
            p_282595_.blitSprite(SLOT_FRAME_SPRITE, i, j, 60, 60);
            p_282595_.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            int k = flag ? -6250336 : -1;
            p_282595_.drawCenteredString(RealmsResetWorldScreen.this.font, this.getMessage(), i + 28, j - 14, k);
        }
    }
}
