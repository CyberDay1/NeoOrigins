package net.minecraft.client.gui.screens.options;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Arrays;
import java.util.stream.Stream;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MouseSettingsScreen extends OptionsSubScreen {
    private static final Component TITLE = Component.translatable("options.mouse_settings.title");

    private static OptionInstance<?>[] options(Options p_345546_) {
        return new OptionInstance[]{
            p_345546_.sensitivity(), p_345546_.invertYMouse(), p_345546_.mouseWheelSensitivity(), p_345546_.discreteMouseScroll(), p_345546_.touchscreen()
        };
    }

    public MouseSettingsScreen(Screen p_344816_, Options p_346286_) {
        super(p_344816_, p_346286_, TITLE);
    }

    @Override
    protected void addOptions() {
        if (InputConstants.isRawMouseInputSupported()) {
            this.list.addSmall(Stream.concat(Arrays.stream(options(this.options)), Stream.of(this.options.rawMouseInput())).toArray(OptionInstance[]::new));
        } else {
            this.list.addSmall(options(this.options));
        }
    }
}
