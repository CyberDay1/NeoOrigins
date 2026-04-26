package net.minecraft.client.gui.screens.worldselection;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.Object2BooleanLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ExperimentsScreen extends Screen {
    protected static final Component TITLE = Component.translatable("selectWorld.experiments");
    protected static final Component INFO = Component.translatable("selectWorld.experiments.info").withStyle(ChatFormatting.RED);
    protected static final int MAIN_CONTENT_WIDTH = 310;
    protected final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final Screen parent;
    private final PackRepository packRepository;
    private final Consumer<PackRepository> output;
    protected final Object2BooleanMap<Pack> packs = new Object2BooleanLinkedOpenHashMap<>();

    public ExperimentsScreen(Screen p_270165_, PackRepository p_270308_, Consumer<PackRepository> p_270392_) {
        super(TITLE);
        this.parent = p_270165_;
        this.packRepository = p_270308_;
        this.output = p_270392_;

        for (Pack pack : p_270308_.getAvailablePacks()) {
            if (pack.getPackSource() == PackSource.FEATURE) {
                this.packs.put(pack, p_270308_.getSelectedPacks().contains(pack));
            }
        }
    }

    @Override
    protected void init() {
        if (net.minecraft.world.flag.FeatureFlags.REGISTRY.hasAnyModdedFlags()) {
            this.minecraft.setScreen(new net.neoforged.neoforge.client.gui.ScrollableExperimentsScreen(this.parent, this.packRepository, this.output));
            return;
        }

        this.layout.addTitleHeader(TITLE, this.font);
        LinearLayout linearlayout = this.layout.addToContents(LinearLayout.vertical());
        linearlayout.addChild(new MultiLineTextWidget(INFO, this.font).setMaxWidth(310), p_293611_ -> p_293611_.paddingBottom(15));
        SwitchGrid.Builder switchgrid$builder = SwitchGrid.builder(310).withInfoUnderneath(2, true).withRowSpacing(4);
        this.packs
            .forEach(
                (p_270880_, p_270874_) -> switchgrid$builder.addSwitch(
                            getHumanReadableTitle(p_270880_),
                            () -> this.packs.getBoolean(p_270880_),
                            p_270491_ -> this.packs.put(p_270880_, p_270491_.booleanValue())
                        )
                        .withInfo(p_270880_.getDescription())
            );
        switchgrid$builder.build(linearlayout::addChild);
        LinearLayout linearlayout1 = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        linearlayout1.addChild(Button.builder(CommonComponents.GUI_DONE, p_270336_ -> this.onDone()).build());
        linearlayout1.addChild(Button.builder(CommonComponents.GUI_CANCEL, p_274702_ -> this.onClose()).build());
        this.layout.visitWidgets(p_321381_ -> {
            AbstractWidget abstractwidget = this.addRenderableWidget(p_321381_);
        });
        this.repositionElements();
    }

    protected static Component getHumanReadableTitle(Pack p_270861_) {
        String s = "dataPack." + p_270861_.getId() + ".name";
        return (Component)(I18n.exists(s) ? Component.translatable(s) : p_270861_.getTitle());
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }

    @Override
    public Component getNarrationMessage() {
        return CommonComponents.joinForNarration(super.getNarrationMessage(), INFO);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    protected void onDone() {
        List<Pack> list = new ArrayList<>(this.packRepository.getSelectedPacks());
        List<Pack> list1 = new ArrayList<>();
        this.packs.forEach((p_270540_, p_270780_) -> {
            list.remove(p_270540_);
            if (p_270780_) {
                list1.add(p_270540_);
            }
        });
        list.addAll(Lists.reverse(list1));
        this.packRepository.setSelected(list.stream().map(Pack::getId).toList());
        this.output.accept(this.packRepository);
    }
}
