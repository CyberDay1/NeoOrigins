package net.minecraft.client.gui.screens.options;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.blaze3d.platform.Window;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GpuWarnlistManager;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class VideoSettingsScreen extends OptionsSubScreen {
    private static final Component TITLE = Component.translatable("options.videoTitle");
    private static final Component FABULOUS = Component.translatable("options.graphics.fabulous").withStyle(ChatFormatting.ITALIC);
    private static final Component WARNING_MESSAGE = Component.translatable("options.graphics.warning.message", FABULOUS, FABULOUS);
    private static final Component WARNING_TITLE = Component.translatable("options.graphics.warning.title").withStyle(ChatFormatting.RED);
    private static final Component BUTTON_ACCEPT = Component.translatable("options.graphics.warning.accept");
    private static final Component BUTTON_CANCEL = Component.translatable("options.graphics.warning.cancel");
    private final GpuWarnlistManager gpuWarnlistManager;
    private final int oldMipmaps;

    private static OptionInstance<?>[] options(Options p_345645_) {
        return new OptionInstance[]{
            p_345645_.graphicsMode(),
            p_345645_.renderDistance(),
            p_345645_.prioritizeChunkUpdates(),
            p_345645_.simulationDistance(),
            p_345645_.ambientOcclusion(),
            p_345645_.framerateLimit(),
            p_345645_.enableVsync(),
            p_345645_.bobView(),
            p_345645_.guiScale(),
            p_345645_.attackIndicator(),
            p_345645_.gamma(),
            p_345645_.cloudStatus(),
            p_345645_.fullscreen(),
            p_345645_.particles(),
            p_345645_.mipmapLevels(),
            p_345645_.entityShadows(),
            p_345645_.screenEffectScale(),
            p_345645_.entityDistanceScaling(),
            p_345645_.fovEffectScale(),
            p_345645_.showAutosaveIndicator(),
            p_345645_.glintSpeed(),
            p_345645_.glintStrength(),
            p_345645_.menuBackgroundBlurriness()
        };
    }

    public VideoSettingsScreen(Screen p_345053_, Minecraft p_346149_, Options p_346198_) {
        super(p_345053_, p_346198_, TITLE);
        this.gpuWarnlistManager = p_346149_.getGpuWarnlistManager();
        this.gpuWarnlistManager.resetWarnings();
        if (p_346198_.graphicsMode().get() == GraphicsStatus.FABULOUS) {
            this.gpuWarnlistManager.dismissWarning();
        }

        this.oldMipmaps = p_346198_.mipmapLevels().get();
    }

    @Override
    protected void addOptions() {
        int i = -1;
        Window window = this.minecraft.getWindow();
        Monitor monitor = window.findBestMonitor();
        int j;
        if (monitor == null) {
            j = -1;
        } else {
            Optional<VideoMode> optional = window.getPreferredFullscreenVideoMode();
            j = optional.map(monitor::getVideoModeIndex).orElse(-1);
        }

        OptionInstance<Integer> optioninstance = new OptionInstance<>(
            "options.fullscreen.resolution",
            OptionInstance.noTooltip(),
            (p_346436_, p_344747_) -> {
                if (monitor == null) {
                    return Component.translatable("options.fullscreen.unavailable");
                } else if (p_344747_ == -1) {
                    return Options.genericValueLabel(p_346436_, Component.translatable("options.fullscreen.current"));
                } else {
                    VideoMode videomode = monitor.getMode(p_344747_);
                    return Options.genericValueLabel(
                        p_346436_,
                        Component.translatable(
                            "options.fullscreen.entry",
                            videomode.getWidth(),
                            videomode.getHeight(),
                            videomode.getRefreshRate(),
                            videomode.getRedBits() + videomode.getGreenBits() + videomode.getBlueBits()
                        )
                    );
                }
            },
            new OptionInstance.IntRange(-1, monitor != null ? monitor.getModeCount() - 1 : -1),
            j,
            p_345666_ -> {
                if (monitor != null) {
                    window.setPreferredFullscreenVideoMode(p_345666_ == -1 ? Optional.empty() : Optional.of(monitor.getMode(p_345666_)));
                }
            }
        );
        this.list.addBig(optioninstance);
        this.list.addBig(this.options.biomeBlendRadius());
        this.list.addSmall(options(this.options));
    }

    @Override
    public void onClose() {
        this.minecraft.getWindow().changeFullscreenVideoMode();
        super.onClose();
    }

    @Override
    public void removed() {
        if (this.options.mipmapLevels().get() != this.oldMipmaps) {
            this.minecraft.updateMaxMipLevel(this.options.mipmapLevels().get());
            this.minecraft.delayTextureReload();
        }

        super.removed();
    }

    @Override
    public boolean mouseClicked(double p_345069_, double p_346434_, int p_344934_) {
        if (super.mouseClicked(p_345069_, p_346434_, p_344934_)) {
            if (this.gpuWarnlistManager.isShowingWarning()) {
                List<Component> list = Lists.newArrayList(WARNING_MESSAGE, CommonComponents.NEW_LINE);
                String s = this.gpuWarnlistManager.getRendererWarnings();
                if (s != null) {
                    list.add(CommonComponents.NEW_LINE);
                    list.add(Component.translatable("options.graphics.warning.renderer", s).withStyle(ChatFormatting.GRAY));
                }

                String s1 = this.gpuWarnlistManager.getVendorWarnings();
                if (s1 != null) {
                    list.add(CommonComponents.NEW_LINE);
                    list.add(Component.translatable("options.graphics.warning.vendor", s1).withStyle(ChatFormatting.GRAY));
                }

                String s2 = this.gpuWarnlistManager.getVersionWarnings();
                if (s2 != null) {
                    list.add(CommonComponents.NEW_LINE);
                    list.add(Component.translatable("options.graphics.warning.version", s2).withStyle(ChatFormatting.GRAY));
                }

                this.minecraft
                    .setScreen(
                        new UnsupportedGraphicsWarningScreen(
                            WARNING_TITLE, list, ImmutableList.of(new UnsupportedGraphicsWarningScreen.ButtonOption(BUTTON_ACCEPT, p_346113_ -> {
                                this.options.graphicsMode().set(GraphicsStatus.FABULOUS);
                                Minecraft.getInstance().levelRenderer.allChanged();
                                this.gpuWarnlistManager.dismissWarning();
                                this.minecraft.setScreen(this);
                            }), new UnsupportedGraphicsWarningScreen.ButtonOption(BUTTON_CANCEL, p_345929_ -> {
                                this.gpuWarnlistManager.dismissWarningAndSkipFabulous();
                                this.minecraft.setScreen(this);
                            }))
                        )
                    );
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean mouseScrolled(double p_344913_, double p_346159_, double p_345166_, double p_345130_) {
        if (Screen.hasControlDown()) {
            OptionInstance<Integer> optioninstance = this.options.guiScale();
            if (optioninstance.values() instanceof OptionInstance.ClampingLazyMaxIntRange optioninstance$clampinglazymaxintrange) {
                int k = optioninstance.get();
                int i = k == 0 ? optioninstance$clampinglazymaxintrange.maxInclusive() + 1 : k;
                int j = i + (int)Math.signum(p_345130_);
                if (j != 0 && j <= optioninstance$clampinglazymaxintrange.maxInclusive() && j >= optioninstance$clampinglazymaxintrange.minInclusive()) {
                    CycleButton<Integer> cyclebutton = (CycleButton<Integer>)this.list.findOption(optioninstance);
                    if (cyclebutton != null) {
                        optioninstance.set(j);
                        cyclebutton.setValue(j);
                        this.list.setScrollAmount(0.0);
                        return true;
                    }
                }
            }

            return false;
        } else {
            return super.mouseScrolled(p_344913_, p_346159_, p_345166_, p_345130_);
        }
    }
}
