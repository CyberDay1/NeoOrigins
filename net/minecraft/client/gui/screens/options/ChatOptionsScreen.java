package net.minecraft.client.gui.screens.options;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ChatOptionsScreen extends OptionsSubScreen {
    private static final Component TITLE = Component.translatable("options.chat.title");

    private static OptionInstance<?>[] options(Options p_345277_) {
        return new OptionInstance[]{
            p_345277_.chatVisibility(),
            p_345277_.chatColors(),
            p_345277_.chatLinks(),
            p_345277_.chatLinksPrompt(),
            p_345277_.chatOpacity(),
            p_345277_.textBackgroundOpacity(),
            p_345277_.chatScale(),
            p_345277_.chatLineSpacing(),
            p_345277_.chatDelay(),
            p_345277_.chatWidth(),
            p_345277_.chatHeightFocused(),
            p_345277_.chatHeightUnfocused(),
            p_345277_.narrator(),
            p_345277_.autoSuggestions(),
            p_345277_.hideMatchedNames(),
            p_345277_.reducedDebugInfo(),
            p_345277_.onlyShowSecureChat()
        };
    }

    public ChatOptionsScreen(Screen p_345947_, Options p_345195_) {
        super(p_345947_, p_345195_, TITLE);
    }

    @Override
    protected void addOptions() {
        this.list.addSmall(options(this.options));
    }
}
