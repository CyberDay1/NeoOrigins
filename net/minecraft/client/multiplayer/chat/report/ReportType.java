package net.minecraft.client.multiplayer.chat.report;

import java.util.Locale;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum ReportType {
    CHAT("chat"),
    SKIN("skin"),
    USERNAME("username");

    private final String backendName;

    private ReportType(String p_300024_) {
        this.backendName = p_300024_.toUpperCase(Locale.ROOT);
    }

    public String backendName() {
        return this.backendName;
    }
}
