package net.minecraft.client.multiplayer.chat.report;

import com.mojang.authlib.minecraft.report.AbuseReport;
import com.mojang.authlib.minecraft.report.AbuseReportLimits;
import com.mojang.datafixers.util.Either;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class Report {
    protected final UUID reportId;
    protected final Instant createdAt;
    protected final UUID reportedProfileId;
    protected String comments = "";
    @Nullable
    protected ReportReason reason;
    protected boolean attested;

    public Report(UUID p_299940_, Instant p_300011_, UUID p_299976_) {
        this.reportId = p_299940_;
        this.createdAt = p_300011_;
        this.reportedProfileId = p_299976_;
    }

    public boolean isReportedPlayer(UUID p_300032_) {
        return p_300032_.equals(this.reportedProfileId);
    }

    public abstract Report copy();

    public abstract Screen createScreen(Screen p_299960_, ReportingContext p_299959_);

    @OnlyIn(Dist.CLIENT)
    public abstract static class Builder<R extends Report> {
        protected final R report;
        protected final AbuseReportLimits limits;

        protected Builder(R p_299998_, AbuseReportLimits p_299869_) {
            this.report = p_299998_;
            this.limits = p_299869_;
        }

        public R report() {
            return this.report;
        }

        public UUID reportedProfileId() {
            return this.report.reportedProfileId;
        }

        public String comments() {
            return this.report.comments;
        }

        public boolean attested() {
            return this.report().attested;
        }

        public void setComments(String p_299837_) {
            this.report.comments = p_299837_;
        }

        @Nullable
        public ReportReason reason() {
            return this.report.reason;
        }

        public void setReason(ReportReason p_299937_) {
            this.report.reason = p_299937_;
        }

        public void setAttested(boolean p_352965_) {
            this.report.attested = p_352965_;
        }

        public abstract boolean hasContent();

        @Nullable
        public Report.CannotBuildReason checkBuildable() {
            return !this.report().attested ? Report.CannotBuildReason.NOT_ATTESTED : null;
        }

        public abstract Either<Report.Result, Report.CannotBuildReason> build(ReportingContext p_299877_);
    }

    @OnlyIn(Dist.CLIENT)
    public static record CannotBuildReason(Component message) {
        public static final Report.CannotBuildReason NO_REASON = new Report.CannotBuildReason(Component.translatable("gui.abuseReport.send.no_reason"));
        public static final Report.CannotBuildReason NO_REPORTED_MESSAGES = new Report.CannotBuildReason(
            Component.translatable("gui.chatReport.send.no_reported_messages")
        );
        public static final Report.CannotBuildReason TOO_MANY_MESSAGES = new Report.CannotBuildReason(
            Component.translatable("gui.chatReport.send.too_many_messages")
        );
        public static final Report.CannotBuildReason COMMENT_TOO_LONG = new Report.CannotBuildReason(
            Component.translatable("gui.abuseReport.send.comment_too_long")
        );
        public static final Report.CannotBuildReason NOT_ATTESTED = new Report.CannotBuildReason(Component.translatable("gui.abuseReport.send.not_attested"));

        public Tooltip tooltip() {
            return Tooltip.create(this.message);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static record Result(UUID id, ReportType reportType, AbuseReport report) {
    }
}
