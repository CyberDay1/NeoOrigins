package net.minecraft.client.gui.screens.options;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FontOptionsScreen extends OptionsSubScreen {
    private static OptionInstance<?>[] options(Options p_346352_) {
        return new OptionInstance[]{p_346352_.forceUnicodeFont(), p_346352_.japaneseGlyphVariants()};
    }

    public FontOptionsScreen(Screen p_345371_, Options p_345464_) {
        super(p_345371_, p_345464_, Component.translatable("options.font.title"));
    }

    @Override
    protected void addOptions() {
        this.list.addSmall(options(this.options));
    }
}
