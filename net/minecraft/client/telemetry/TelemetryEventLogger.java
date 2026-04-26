package net.minecraft.client.telemetry;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface TelemetryEventLogger {
    void log(TelemetryEventInstance p_261961_);
}
