package net.minecraft.client.gui.screens.options.controls;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.lang3.ArrayUtils;

@OnlyIn(Dist.CLIENT)
public class KeyBindsList extends ContainerObjectSelectionList<KeyBindsList.Entry> {
    private static final int ITEM_HEIGHT = 20;
    final KeyBindsScreen keyBindsScreen;
    private int maxNameWidth;

    public KeyBindsList(KeyBindsScreen p_345102_, Minecraft p_346132_) {
        super(p_346132_, p_345102_.width, p_345102_.layout.getContentHeight(), p_345102_.layout.getHeaderHeight(), 20);
        this.keyBindsScreen = p_345102_;
        KeyMapping[] akeymapping = ArrayUtils.clone((KeyMapping[])p_346132_.options.keyMappings);
        Arrays.sort((Object[])akeymapping);
        String s = null;

        for (KeyMapping keymapping : akeymapping) {
            String s1 = keymapping.getCategory();
            if (!s1.equals(s)) {
                s = s1;
                this.addEntry(new KeyBindsList.CategoryEntry(Component.translatable(s1)));
            }

            Component component = keymapping.getDisplayName();
            int i = p_346132_.font.width(component);
            if (i > this.maxNameWidth) {
                this.maxNameWidth = i;
            }

            this.addEntry(new KeyBindsList.KeyEntry(keymapping, component));
        }
    }

    public void resetMappingAndUpdateButtons() {
        KeyMapping.resetMapping();
        this.refreshEntries();
    }

    public void refreshEntries() {
        this.children().forEach(KeyBindsList.Entry::refreshEntry);
    }

    @Override
    public int getRowWidth() {
        return 340;
    }

    @OnlyIn(Dist.CLIENT)
    public class CategoryEntry extends KeyBindsList.Entry {
        final Component name;
        private final int width;

        public CategoryEntry(Component p_345224_) {
            this.name = p_345224_;
            this.width = KeyBindsList.this.minecraft.font.width(this.name);
        }

        @Override
        public void render(
            GuiGraphics p_345402_,
            int p_345541_,
            int p_345306_,
            int p_346166_,
            int p_346154_,
            int p_345075_,
            int p_346184_,
            int p_346385_,
            boolean p_346139_,
            float p_345189_
        ) {
            p_345402_.drawString(
                KeyBindsList.this.minecraft.font, this.name, KeyBindsList.this.width / 2 - this.width / 2, p_345306_ + p_345075_ - 9 - 1, -1, false
            );
        }

        @Nullable
        @Override
        public ComponentPath nextFocusPath(FocusNavigationEvent p_344970_) {
            return null;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Collections.emptyList();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(new NarratableEntry() {
                @Override
                public NarratableEntry.NarrationPriority narrationPriority() {
                    return NarratableEntry.NarrationPriority.HOVERED;
                }

                @Override
                public void updateNarration(NarrationElementOutput p_344973_) {
                    p_344973_.add(NarratedElementType.TITLE, CategoryEntry.this.name);
                }
            });
        }

        @Override
        protected void refreshEntry() {
        }
    }

    @OnlyIn(Dist.CLIENT)
    public abstract static class Entry extends ContainerObjectSelectionList.Entry<KeyBindsList.Entry> {
        abstract void refreshEntry();
    }

    @OnlyIn(Dist.CLIENT)
    public class KeyEntry extends KeyBindsList.Entry {
        private static final Component RESET_BUTTON_TITLE = Component.translatable("controls.reset");
        private static final int PADDING = 10;
        private final KeyMapping key;
        private final Component name;
        private final Button changeButton;
        private final Button resetButton;
        private boolean hasCollision = false;

        KeyEntry(KeyMapping p_345998_, Component p_345196_) {
            this.key = p_345998_;
            this.name = p_345196_;
            this.changeButton = Button.builder(p_345196_, p_345593_ -> {
                    KeyBindsList.this.keyBindsScreen.selectedKey = p_345998_;
                    KeyBindsList.this.resetMappingAndUpdateButtons();
                })
                .bounds(0, 0, 75, 20)
                .createNarration(
                    p_346090_ -> p_345998_.isUnbound()
                            ? Component.translatable("narrator.controls.unbound", p_345196_)
                            : Component.translatable("narrator.controls.bound", p_345196_, p_346090_.get())
                )
                .build();
            this.resetButton = Button.builder(RESET_BUTTON_TITLE, p_346334_ -> {
                this.key.setToDefault();
                KeyBindsList.this.minecraft.options.setKey(p_345998_, p_345998_.getDefaultKey());
                KeyBindsList.this.resetMappingAndUpdateButtons();
            }).bounds(0, 0, 50, 20).createNarration(p_344899_ -> Component.translatable("narrator.controls.reset", p_345196_)).build();
            this.refreshEntry();
        }

        @Override
        public void render(
            GuiGraphics p_345065_,
            int p_345504_,
            int p_345678_,
            int p_344740_,
            int p_345885_,
            int p_344888_,
            int p_345213_,
            int p_344829_,
            boolean p_346415_,
            float p_345934_
        ) {
            int i = KeyBindsList.this.getScrollbarPosition() - this.resetButton.getWidth() - 10;
            int j = p_345678_ - 2;
            this.resetButton.setPosition(i, j);
            this.resetButton.render(p_345065_, p_345213_, p_344829_, p_345934_);
            int k = i - 5 - this.changeButton.getWidth();
            this.changeButton.setPosition(k, j);
            this.changeButton.render(p_345065_, p_345213_, p_344829_, p_345934_);
            p_345065_.drawString(KeyBindsList.this.minecraft.font, this.name, p_344740_, p_345678_ + p_344888_ / 2 - 9 / 2, -1);
            if (this.hasCollision) {
                int l = 3;
                int i1 = this.changeButton.getX() - 6;
                p_345065_.fill(i1, p_345678_ - 1, i1 + 3, p_345678_ + p_344888_, -65536);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(this.changeButton, this.resetButton);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(this.changeButton, this.resetButton);
        }

        @Override
        protected void refreshEntry() {
            this.changeButton.setMessage(this.key.getTranslatedKeyMessage());
            this.resetButton.active = !this.key.isDefault();
            this.hasCollision = false;
            MutableComponent mutablecomponent = Component.empty();
            if (!this.key.isUnbound()) {
                for (KeyMapping keymapping : KeyBindsList.this.minecraft.options.keyMappings) {
                    if ((keymapping != this.key && this.key.same(keymapping)) || keymapping.hasKeyModifierConflict(this.key)) { // Neo: gracefully handle conflicts like SHIFT vs SHIFT+G
                        if (this.hasCollision) {
                            mutablecomponent.append(", ");
                        }

                        this.hasCollision = true;
                        mutablecomponent.append(keymapping.getDisplayName());
                    }
                }
            }

            if (this.hasCollision) {
                this.changeButton
                    .setMessage(
                        Component.literal("[ ")
                            .append(this.changeButton.getMessage().copy().withStyle(ChatFormatting.WHITE))
                            .append(" ]")
                            .withStyle(ChatFormatting.RED)
                    );
                this.changeButton.setTooltip(Tooltip.create(Component.translatable("controls.keybinds.duplicateKeybinds", mutablecomponent)));
            } else {
                this.changeButton.setTooltip(null);
            }

            if (KeyBindsList.this.keyBindsScreen.selectedKey == this.key) {
                this.changeButton
                    .setMessage(
                        Component.literal("> ")
                            .append(this.changeButton.getMessage().copy().withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE))
                            .append(" <")
                            .withStyle(ChatFormatting.YELLOW)
                    );
            }
        }
    }
}
