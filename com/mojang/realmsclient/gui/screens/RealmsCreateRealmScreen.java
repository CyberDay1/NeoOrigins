package com.mojang.realmsclient.gui.screens;

import com.mojang.realmsclient.RealmsMainScreen;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.util.WorldGenerationInfo;
import com.mojang.realmsclient.util.task.CreateSnapshotRealmTask;
import com.mojang.realmsclient.util.task.RealmCreationTask;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.CommonLayouts;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.realms.RealmsScreen;
import net.minecraft.util.StringUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RealmsCreateRealmScreen extends RealmsScreen {
    private static final Component CREATE_REALM_TEXT = Component.translatable("mco.selectServer.create");
    private static final Component NAME_LABEL = Component.translatable("mco.configure.world.name");
    private static final Component DESCRIPTION_LABEL = Component.translatable("mco.configure.world.description");
    private static final int BUTTON_SPACING = 10;
    private static final int CONTENT_WIDTH = 210;
    private final RealmsMainScreen lastScreen;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private EditBox nameBox;
    private EditBox descriptionBox;
    private final Runnable createWorldRunnable;

    public RealmsCreateRealmScreen(RealmsMainScreen p_88575_, RealmsServer p_88574_) {
        super(CREATE_REALM_TEXT);
        this.lastScreen = p_88575_;
        this.createWorldRunnable = () -> this.createWorld(p_88574_);
    }

    public RealmsCreateRealmScreen(RealmsMainScreen p_306218_, long p_305942_) {
        super(CREATE_REALM_TEXT);
        this.lastScreen = p_306218_;
        this.createWorldRunnable = () -> this.createSnapshotWorld(p_305942_);
    }

    @Override
    public void init() {
        this.layout.addTitleHeader(this.title, this.font);
        LinearLayout linearlayout = this.layout.addToContents(LinearLayout.vertical()).spacing(10);
        Button button = Button.builder(CommonComponents.GUI_CONTINUE, p_305625_ -> this.createWorldRunnable.run()).build();
        button.active = false;
        this.nameBox = new EditBox(this.font, 210, 20, NAME_LABEL);
        this.nameBox.setResponder(p_329650_ -> button.active = !StringUtil.isBlank(p_329650_));
        this.descriptionBox = new EditBox(this.font, 210, 20, DESCRIPTION_LABEL);
        linearlayout.addChild(CommonLayouts.labeledElement(this.font, this.nameBox, NAME_LABEL));
        linearlayout.addChild(CommonLayouts.labeledElement(this.font, this.descriptionBox, DESCRIPTION_LABEL));
        LinearLayout linearlayout1 = this.layout.addToFooter(LinearLayout.horizontal().spacing(10));
        linearlayout1.addChild(button);
        linearlayout1.addChild(Button.builder(CommonComponents.GUI_BACK, p_293570_ -> this.onClose()).build());
        this.layout.visitWidgets(p_321338_ -> {
            AbstractWidget abstractwidget = this.addRenderableWidget(p_321338_);
        });
        this.repositionElements();
    }

    @Override
    protected void setInitialFocus() {
        this.setInitialFocus(this.nameBox);
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }

    private void createWorld(RealmsServer p_305781_) {
        RealmCreationTask realmcreationtask = new RealmCreationTask(p_305781_.id, this.nameBox.getValue(), this.descriptionBox.getValue());
        RealmsResetWorldScreen realmsresetworldscreen = RealmsResetWorldScreen.forNewRealm(
            this, p_305781_, realmcreationtask, () -> this.minecraft.execute(() -> {
                    RealmsMainScreen.refreshServerList();
                    this.minecraft.setScreen(this.lastScreen);
                })
        );
        this.minecraft.setScreen(realmsresetworldscreen);
    }

    private void createSnapshotWorld(long p_305809_) {
        Screen screen = new RealmsResetNormalWorldScreen(
            p_305627_ -> {
                if (p_305627_ == null) {
                    this.minecraft.setScreen(this);
                } else {
                    this.minecraft
                        .setScreen(
                            new RealmsLongRunningMcoTaskScreen(
                                this,
                                new CreateSnapshotRealmTask(this.lastScreen, p_305809_, p_305627_, this.nameBox.getValue(), this.descriptionBox.getValue())
                            )
                        );
                }
            },
            CREATE_REALM_TEXT
        );
        this.minecraft.setScreen(screen);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }
}
