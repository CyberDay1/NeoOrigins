package net.minecraft.client.gui.screens.options;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LanguageSelectScreen extends OptionsSubScreen {
    private static final Component WARNING_LABEL = Component.translatable("options.languageAccuracyWarning").withColor(-4539718);
    private static final int FOOTER_HEIGHT = 53;
    private LanguageSelectScreen.LanguageSelectionList languageSelectionList;
    final LanguageManager languageManager;

    public LanguageSelectScreen(Screen p_345780_, Options p_344823_, LanguageManager p_344876_) {
        super(p_345780_, p_344823_, Component.translatable("options.language.title"));
        this.languageManager = p_344876_;
        this.layout.setFooterHeight(53);
    }

    @Override
    protected void addContents() {
        this.languageSelectionList = this.layout.addToContents(new LanguageSelectScreen.LanguageSelectionList(this.minecraft));
    }

    @Override
    protected void addOptions() {
    }

    @Override
    protected void addFooter() {
        LinearLayout linearlayout = this.layout.addToFooter(LinearLayout.vertical()).spacing(8);
        linearlayout.defaultCellSetting().alignHorizontallyCenter();
        linearlayout.addChild(new StringWidget(WARNING_LABEL, this.font));
        LinearLayout linearlayout1 = linearlayout.addChild(LinearLayout.horizontal().spacing(8));
        linearlayout1.addChild(
            Button.builder(Component.translatable("options.font"), p_346158_ -> this.minecraft.setScreen(new FontOptionsScreen(this, this.options))).build()
        );
        linearlayout1.addChild(Button.builder(CommonComponents.GUI_DONE, p_346094_ -> this.onDone()).build());
    }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        this.languageSelectionList.updateSize(this.width, this.layout);
    }

    void onDone() {
        LanguageSelectScreen.LanguageSelectionList.Entry languageselectscreen$languageselectionlist$entry = this.languageSelectionList.getSelected();
        if (languageselectscreen$languageselectionlist$entry != null
            && !languageselectscreen$languageselectionlist$entry.code.equals(this.languageManager.getSelected())) {
            this.languageManager.setSelected(languageselectscreen$languageselectionlist$entry.code);
            this.options.languageCode = languageselectscreen$languageselectionlist$entry.code;
            this.minecraft.reloadResourcePacks();
        }

        this.minecraft.setScreen(this.lastScreen);
    }

    @OnlyIn(Dist.CLIENT)
    class LanguageSelectionList extends ObjectSelectionList<LanguageSelectScreen.LanguageSelectionList.Entry> {
        public LanguageSelectionList(Minecraft p_345567_) {
            super(p_345567_, LanguageSelectScreen.this.width, LanguageSelectScreen.this.height - 33 - 53, 33, 18);
            String s = LanguageSelectScreen.this.languageManager.getSelected();
            LanguageSelectScreen.this.languageManager
                .getLanguages()
                .forEach(
                    (p_346086_, p_345379_) -> {
                        LanguageSelectScreen.LanguageSelectionList.Entry languageselectscreen$languageselectionlist$entry = new LanguageSelectScreen.LanguageSelectionList.Entry(
                            p_346086_, p_345379_
                        );
                        this.addEntry(languageselectscreen$languageselectionlist$entry);
                        if (s.equals(p_346086_)) {
                            this.setSelected(languageselectscreen$languageselectionlist$entry);
                        }
                    }
                );
            if (this.getSelected() != null) {
                this.centerScrollOn(this.getSelected());
            }
        }

        @Override
        public int getRowWidth() {
            return super.getRowWidth() + 50;
        }

        @OnlyIn(Dist.CLIENT)
        public class Entry extends ObjectSelectionList.Entry<LanguageSelectScreen.LanguageSelectionList.Entry> {
            final String code;
            private final Component language;
            private long lastClickTime;

            public Entry(String p_346435_, LanguageInfo p_345619_) {
                this.code = p_346435_;
                this.language = p_345619_.toComponent();
            }

            @Override
            public void render(
                GuiGraphics p_345300_,
                int p_345469_,
                int p_345328_,
                int p_345700_,
                int p_345311_,
                int p_345185_,
                int p_344805_,
                int p_345963_,
                boolean p_345912_,
                float p_346091_
            ) {
                p_345300_.drawCenteredString(
                    LanguageSelectScreen.this.font, this.language, LanguageSelectionList.this.width / 2, p_345328_ + p_345185_ / 2 - 9 / 2, -1
                );
            }

            @Override
            public boolean keyPressed(int p_346403_, int p_345881_, int p_345858_) {
                if (CommonInputs.selected(p_346403_)) {
                    this.select();
                    LanguageSelectScreen.this.onDone();
                    return true;
                } else {
                    return super.keyPressed(p_346403_, p_345881_, p_345858_);
                }
            }

            @Override
            public boolean mouseClicked(double p_344965_, double p_345385_, int p_345080_) {
                this.select();
                if (Util.getMillis() - this.lastClickTime < 250L) {
                    LanguageSelectScreen.this.onDone();
                }

                this.lastClickTime = Util.getMillis();
                return super.mouseClicked(p_344965_, p_345385_, p_345080_);
            }

            private void select() {
                LanguageSelectionList.this.setSelected(this);
            }

            @Override
            public Component getNarration() {
                return Component.translatable("narrator.select", this.language);
            }
        }
    }
}
