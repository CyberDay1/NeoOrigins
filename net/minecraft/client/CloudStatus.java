package net.minecraft.client;

import com.mojang.serialization.Codec;
import net.minecraft.util.OptionEnum;
import net.minecraft.util.StringRepresentable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum CloudStatus implements OptionEnum, StringRepresentable {
    OFF(0, "false", "options.off"),
    FAST(1, "fast", "options.clouds.fast"),
    FANCY(2, "true", "options.clouds.fancy");

    public static final Codec<CloudStatus> CODEC = StringRepresentable.fromEnum(CloudStatus::values);
    private final int id;
    private final String legacyName;
    private final String key;

    private CloudStatus(int p_231334_, String p_231335_, String p_296084_) {
        this.id = p_231334_;
        this.legacyName = p_231335_;
        this.key = p_296084_;
    }

    @Override
    public String getSerializedName() {
        return this.legacyName;
    }

    @Override
    public int getId() {
        return this.id;
    }

    @Override
    public String getKey() {
        return this.key;
    }
}
