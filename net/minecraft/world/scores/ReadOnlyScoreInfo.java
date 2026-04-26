package net.minecraft.world.scores;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;

public interface ReadOnlyScoreInfo {
    int value();

    boolean isLocked();

    @Nullable
    NumberFormat numberFormat();

    default MutableComponent formatValue(NumberFormat p_313924_) {
        return Objects.requireNonNullElse(this.numberFormat(), p_313924_).format(this.value());
    }

    static MutableComponent safeFormatValue(@Nullable ReadOnlyScoreInfo p_313916_, NumberFormat p_313801_) {
        return p_313916_ != null ? p_313916_.formatValue(p_313801_) : p_313801_.format(0);
    }
}
