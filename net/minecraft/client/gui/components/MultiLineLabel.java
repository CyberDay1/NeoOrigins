package net.minecraft.client.gui.components;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface MultiLineLabel {
    MultiLineLabel EMPTY = new MultiLineLabel() {
        @Override
        public void renderCentered(GuiGraphics p_283287_, int p_94383_, int p_94384_) {
        }

        @Override
        public void renderCentered(GuiGraphics p_283208_, int p_210825_, int p_210826_, int p_210827_, int p_210828_) {
        }

        @Override
        public void renderLeftAligned(GuiGraphics p_283077_, int p_94379_, int p_94380_, int p_282157_, int p_282742_) {
        }

        @Override
        public int renderLeftAlignedNoShadow(GuiGraphics p_283645_, int p_94389_, int p_94390_, int p_94391_, int p_94392_) {
            return p_94390_;
        }

        @Override
        public int getLineCount() {
            return 0;
        }

        @Override
        public int getWidth() {
            return 0;
        }
    };

    static MultiLineLabel create(Font p_94351_, Component... p_94352_) {
        return create(p_94351_, Integer.MAX_VALUE, Integer.MAX_VALUE, p_94352_);
    }

    static MultiLineLabel create(Font p_94346_, int p_94348_, Component... p_352900_) {
        return create(p_94346_, p_94348_, Integer.MAX_VALUE, p_352900_);
    }

    static MultiLineLabel create(Font p_169037_, Component p_352901_, int p_352917_) {
        return create(p_169037_, p_352917_, Integer.MAX_VALUE, p_352901_);
    }

    static MultiLineLabel create(final Font p_94342_, final int p_94344_, final int p_352914_, final Component... p_352955_) {
        return p_352955_.length == 0 ? EMPTY : new MultiLineLabel() {
            @Nullable
            private List<MultiLineLabel.TextAndWidth> cachedTextAndWidth;
            @Nullable
            private Language splitWithLanguage;

            @Override
            public void renderCentered(GuiGraphics p_281603_, int p_281267_, int p_281819_) {
                this.renderCentered(p_281603_, p_281267_, p_281819_, 9, -1);
            }

            @Override
            public void renderCentered(GuiGraphics p_283492_, int p_283184_, int p_282078_, int p_352944_, int p_352919_) {
                int i = p_282078_;

                for (MultiLineLabel.TextAndWidth multilinelabel$textandwidth : this.getSplitMessage()) {
                    p_283492_.drawCenteredString(p_94342_, multilinelabel$textandwidth.text, p_283184_, i, p_352919_);
                    i += p_352944_;
                }
            }

            @Override
            public void renderLeftAligned(GuiGraphics p_282318_, int p_283665_, int p_283416_, int p_281919_, int p_281686_) {
                int i = p_283416_;

                for (MultiLineLabel.TextAndWidth multilinelabel$textandwidth : this.getSplitMessage()) {
                    p_282318_.drawString(p_94342_, multilinelabel$textandwidth.text, p_283665_, i, p_281686_);
                    i += p_281919_;
                }
            }

            @Override
            public int renderLeftAlignedNoShadow(GuiGraphics p_281782_, int p_282841_, int p_283554_, int p_282768_, int p_283499_) {
                int i = p_283554_;

                for (MultiLineLabel.TextAndWidth multilinelabel$textandwidth : this.getSplitMessage()) {
                    p_281782_.drawString(p_94342_, multilinelabel$textandwidth.text, p_282841_, i, p_283499_, false);
                    i += p_282768_;
                }

                return i;
            }

            private List<MultiLineLabel.TextAndWidth> getSplitMessage() {
                Language language = Language.getInstance();
                if (this.cachedTextAndWidth != null && language == this.splitWithLanguage) {
                    return this.cachedTextAndWidth;
                } else {
                    this.splitWithLanguage = language;
                    List<FormattedCharSequence> list = new ArrayList<>();

                    for (Component component : p_352955_) {
                        list.addAll(p_94342_.split(component, p_94344_));
                    }

                    this.cachedTextAndWidth = new ArrayList<>();

                    for (FormattedCharSequence formattedcharsequence : list.subList(0, Math.min(list.size(), p_352914_))) {
                        this.cachedTextAndWidth.add(new MultiLineLabel.TextAndWidth(formattedcharsequence, p_94342_.width(formattedcharsequence)));
                    }

                    return this.cachedTextAndWidth;
                }
            }

            @Override
            public int getLineCount() {
                return this.getSplitMessage().size();
            }

            @Override
            public int getWidth() {
                return Math.min(p_94344_, this.getSplitMessage().stream().mapToInt(MultiLineLabel.TextAndWidth::width).max().orElse(0));
            }
        };
    }

    void renderCentered(GuiGraphics p_281785_, int p_94337_, int p_94338_);

    void renderCentered(GuiGraphics p_281749_, int p_94334_, int p_94335_, int p_352960_, int p_352902_);

    void renderLeftAligned(GuiGraphics p_282655_, int p_94365_, int p_94366_, int p_94367_, int p_94368_);

    int renderLeftAlignedNoShadow(GuiGraphics p_281982_, int p_94354_, int p_94355_, int p_94356_, int p_94357_);

    int getLineCount();

    int getWidth();

    @OnlyIn(Dist.CLIENT)
    public static record TextAndWidth(FormattedCharSequence text, int width) {
    }
}
