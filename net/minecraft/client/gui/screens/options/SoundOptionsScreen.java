package net.minecraft.client.gui.screens.options;

import java.util.Arrays;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoundOptionsScreen extends OptionsSubScreen {
    private static final Component TITLE = Component.translatable("options.sounds.title");

    private static OptionInstance<?>[] buttonOptions(Options p_345044_) {
        return new OptionInstance[]{p_345044_.showSubtitles(), p_345044_.directionalAudio()};
    }

    public SoundOptionsScreen(Screen p_345995_, Options p_345465_) {
        super(p_345995_, p_345465_, TITLE);
    }

    @Override
    protected void addOptions() {
        this.list.addBig(this.options.getSoundSourceOptionInstance(SoundSource.MASTER));
        this.list.addSmall(this.getAllSoundOptionsExceptMaster());
        this.list.addBig(this.options.soundDevice());
        this.list.addSmall(buttonOptions(this.options));
    }

    private OptionInstance<?>[] getAllSoundOptionsExceptMaster() {
        return Arrays.stream(SoundSource.values())
            .filter(p_346376_ -> p_346376_ != SoundSource.MASTER)
            .map(p_344738_ -> this.options.getSoundSourceOptionInstance(p_344738_))
            .toArray(OptionInstance[]::new);
    }
}
