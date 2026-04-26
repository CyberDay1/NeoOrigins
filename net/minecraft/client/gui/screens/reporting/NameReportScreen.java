package net.minecraft.client.gui.screens.reporting;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.CommonLayouts;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.chat.report.NameReport;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class NameReportScreen extends AbstractReportScreen<NameReport.Builder> {
    private static final Component TITLE = Component.translatable("gui.abuseReport.name.title");
    private MultiLineEditBox commentBox;

    private NameReportScreen(Screen p_299832_, ReportingContext p_300026_, NameReport.Builder p_299866_) {
        super(TITLE, p_299832_, p_300026_, p_299866_);
    }

    public NameReportScreen(Screen p_299947_, ReportingContext p_299966_, UUID p_299912_, String p_299935_) {
        this(p_299947_, p_299966_, new NameReport.Builder(p_299912_, p_299935_, p_299966_.sender().reportLimits()));
    }

    public NameReportScreen(Screen p_299861_, ReportingContext p_300015_, NameReport p_299994_) {
        this(p_299861_, p_300015_, new NameReport.Builder(p_299994_, p_300015_.sender().reportLimits()));
    }

    @Override
    protected void addContent() {
        Component component = Component.literal(this.reportBuilder.report().getReportedName()).withStyle(ChatFormatting.YELLOW);
        this.layout
            .addChild(
                new StringWidget(Component.translatable("gui.abuseReport.name.reporting", component), this.font),
                p_300033_ -> p_300033_.alignHorizontallyLeft().padding(0, 8)
            );
        this.commentBox = this.createCommentBox(280, 9 * 8, p_352668_ -> {
            this.reportBuilder.setComments(p_352668_);
            this.onReportChanged();
        });
        this.layout.addChild(CommonLayouts.labeledElement(this.font, this.commentBox, MORE_COMMENTS_LABEL, p_299902_ -> p_299902_.paddingBottom(12)));
    }

    @Override
    public boolean mouseReleased(double p_299979_, double p_299840_, int p_299849_) {
        return super.mouseReleased(p_299979_, p_299840_, p_299849_) ? true : this.commentBox.mouseReleased(p_299979_, p_299840_, p_299849_);
    }
}
