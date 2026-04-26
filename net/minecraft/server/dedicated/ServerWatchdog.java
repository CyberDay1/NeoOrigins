package net.minecraft.server.dedicated;

import com.google.common.collect.Streams;
import com.mojang.logging.LogUtils;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportType;
import net.minecraft.Util;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.TimeUtil;
import net.minecraft.world.level.GameRules;
import org.slf4j.Logger;

public class ServerWatchdog implements Runnable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MAX_SHUTDOWN_TIME = 10000L;
    private static final int SHUTDOWN_STATUS = 1;
    private final DedicatedServer server;
    private final long maxTickTimeNanos;

    public ServerWatchdog(DedicatedServer p_139786_) {
        this.server = p_139786_;
        this.maxTickTimeNanos = p_139786_.getMaxTickLength() * TimeUtil.NANOSECONDS_PER_MILLISECOND;
    }

    @Override
    public void run() {
        while (this.server.isRunning()) {
            long i = this.server.getNextTickTime();
            long j = Util.getNanos();
            long k = j - i;
            if (k > this.maxTickTimeNanos) {
                LOGGER.error(
                    LogUtils.FATAL_MARKER,
                    "A single server tick took {} seconds (should be max {})",
                    String.format(Locale.ROOT, "%.2f", (float)k / (float)TimeUtil.NANOSECONDS_PER_SECOND),
                    String.format(Locale.ROOT, "%.2f", this.server.tickRateManager().millisecondsPerTick() / (float)TimeUtil.MILLISECONDS_PER_SECOND)
                );
                LOGGER.error(LogUtils.FATAL_MARKER, "Considering it to be crashed, server will forcibly shutdown.");
                ThreadMXBean threadmxbean = ManagementFactory.getThreadMXBean();
                ThreadInfo[] athreadinfo = threadmxbean.dumpAllThreads(true, true);
                StringBuilder stringbuilder = new StringBuilder();
                Error error = new Error(String.format(java.util.Locale.ENGLISH, "ServerHangWatchdog detected that a single server tick took %.2f seconds (should be max 0.05)", k / 1000F)); // Forge: don't just make a crash report with a seemingly-inexplicable Error

                for (ThreadInfo threadinfo : athreadinfo) {
                    if (threadinfo.getThreadId() == this.server.getRunningThread().getId()) {
                        error.setStackTrace(threadinfo.getStackTrace());
                    }

                    stringbuilder.append(net.neoforged.neoforge.logging.ThreadInfoUtil.getEntireStacktrace(threadinfo));
                    stringbuilder.append("\n");
                }

                CrashReport crashreport = new CrashReport("Watching Server", error);
                this.server.fillSystemReport(crashreport.getSystemReport());
                CrashReportCategory crashreportcategory = crashreport.addCategory("Thread Dump");
                crashreportcategory.setDetail("Threads", stringbuilder);
                CrashReportCategory crashreportcategory1 = crashreport.addCategory("Performance stats");
                crashreportcategory1.setDetail(
                    "Random tick rate", () -> this.server.getWorldData().getGameRules().getRule(GameRules.RULE_RANDOMTICKING).toString()
                );
                crashreportcategory1.setDetail(
                    "Level stats",
                    () -> Streams.stream(this.server.getAllLevels())
                            .map(p_340669_ -> p_340669_.dimension() + ": " + p_340669_.getWatchdogStats())
                            .collect(Collectors.joining(",\n"))
                );
                Bootstrap.realStdoutPrintln("Crash report:\n" + crashreport.getFriendlyReport(ReportType.CRASH));
                Path path = this.server.getServerDirectory().resolve("crash-reports").resolve("crash-" + Util.getFilenameFormattedDateTime() + "-server.txt");
                if (crashreport.saveToFile(path, ReportType.CRASH)) {
                    LOGGER.error("This crash report has been saved to: {}", path.toAbsolutePath());
                } else {
                    LOGGER.error("We were unable to save this crash report to disk.");
                }

                this.exit();
            }

            try {
                Thread.sleep((i + this.maxTickTimeNanos - j) / TimeUtil.NANOSECONDS_PER_MILLISECOND);
            } catch (InterruptedException interruptedexception) {
            }
        }
    }

    private void exit() {
        try {
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Runtime.getRuntime().halt(1);
                }
            }, 10000L);
            System.exit(1);
        } catch (Throwable throwable) {
            Runtime.getRuntime().halt(1);
        }
    }
}
